package com.aquiresolve.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.models.OsChecklistData
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gera o PDF da Ordem de Serviço (OS) com checklist, fotos antes/depois,
 * materiais usados e dados de GPS — pronto para ser compartilhado via WhatsApp/e-mail.
 *
 * Usa o PdfDocument nativo do Android (sem dependências extras). As imagens remotas
 * As fotos remotas são baixadas com o Glide de forma síncrona em IO.
 */
object OsPdfGenerator {

    private const val PAGE_WIDTH = 595   // A4 em pontos (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    /**
     * Gera o arquivo PDF e devolve o [File] resultante no cacheDir do app.
     * Deve ser chamado de uma coroutine (faz download de imagens).
     */
    suspend fun generate(
        context: Context,
        order: OrderData,
        checklist: OsChecklistData
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()

        val title = Paint().apply {
            color = Color.parseColor("#1B5E20"); textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#0D47A1"); textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#555555"); textSize = 10.5f; isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.BLACK; textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val linePaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 0.8f }

        val ctx = DrawContext(doc, title, sectionPaint, labelPaint, valuePaint, linePaint)
        ctx.newPage()

        // Cabeçalho
        ctx.canvas.drawText("AquiResolve — Ordem de Serviço", MARGIN, ctx.y, title)
        ctx.y += 22f
        ctx.canvas.drawText("Protocolo: ${order.protocol.ifBlank { order.id }}", MARGIN, ctx.y, valuePaint)
        ctx.y += 14f
        ctx.canvas.drawText("Emitido em ${dateFormat.format(java.util.Date())}", MARGIN, ctx.y, labelPaint)
        ctx.y += 10f
        ctx.hr()

        // Dados do pedido
        ctx.section("Dados do Atendimento")
        ctx.row("Cliente", order.clientName)
        ctx.row("Prestador", order.assignedProviderName ?: "Não atribuído")
        ctx.row("Serviço", order.serviceName.ifBlank { order.serviceType })
        ctx.row("Endereço", listOfNotNull(order.address.ifBlank { null }, order.city.ifBlank { null }, order.state.ifBlank { null }).joinToString(", "))
        ctx.row("Status", traduzStatus(order.status))
        val valor = order.finalPrice ?: order.estimatedPrice
        if (valor > 0) ctx.row("Valor", "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor))
        if (order.providerCommission > 0) ctx.row("Comissão do prestador", "R$ " + String.format(Locale("pt", "BR"), "%.2f", order.providerCommission))

        // GPS / horários
        ctx.section("Localização e Horários")
        if (checklist.startLatitude != null && checklist.startLongitude != null) {
            ctx.row("Coordenadas GPS", String.format(Locale.US, "%.6f, %.6f", checklist.startLatitude, checklist.startLongitude))
        }
        checklist.startedAt?.toDate()?.let { ctx.row("Iniciado em", dateFormat.format(it)) }
        checklist.completedAt?.toDate()?.let { ctx.row("Concluído em", dateFormat.format(it)) }

        // Checklist
        ctx.section("Checklist")
        ctx.boolRow("Cliente presente", checklist.clientPresent)
        ctx.boolRow("Serviço executado conforme solicitado", checklist.executedAsRequested ?: checklist.serviceMatches)
        ctx.boolRow("Necessitou material adicional", checklist.additionalService)
        ctx.boolRow("Usou materiais/suprimentos", checklist.materialsUsed)
        ctx.boolRow("Local limpo após execução", checklist.cleanAfterService)
        ctx.boolRow("Avarias visíveis", checklist.visibleDamage)
        ctx.boolRow("Troca de peças", checklist.partsReplaced)
        ctx.boolRow("Serviço concluído com sucesso", checklist.serviceCompleted)
        if (checklist.serviceDescription.isNotEmpty()) {
            ctx.row("Categorias", checklist.serviceDescription.joinToString(", "))
        }
        ctx.row("Resolução do problema", traduzResolucao(checklist.problemResolution))

        // Relatório
        ctx.section("Relatório do Serviço")
        ctx.paragraph(checklist.executionDescription.ifBlank { "—" })
        if (checklist.preExistingDamages.isNotBlank()) {
            ctx.row("Avarias pré-existentes", "")
            ctx.paragraph(checklist.preExistingDamages)
        }
        if (checklist.observations.isNotBlank()) {
            ctx.row("Observações", "")
            ctx.paragraph(checklist.observations)
        }
        if (checklist.materialsUsed == true && checklist.materialsDescription.isNotBlank()) {
            ctx.row("Materiais/suprimentos usados", "")
            ctx.paragraph(checklist.materialsDescription)
        }

        // Fotos
        drawPhotoSection(context, ctx, "Fotos — Chegada / Antes", checklist.photosBefore)
        if (checklist.photosDuring.isNotEmpty()) {
            drawPhotoSection(context, ctx, "Fotos — Durante", checklist.photosDuring)
        }
        drawPhotoSection(context, ctx, "Fotos — Pós-serviço", checklist.photosAfter)

        ctx.section("Finalização")
        ctx.row("Método", "Código único do cliente")
        ctx.row("Status da OS", checklist.status)

        ctx.finishPage()

        val dir = File(context.cacheDir, "os_pdf").apply { mkdirs() }
        val safeProtocol = (order.protocol.ifBlank { order.id }).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "OS_$safeProtocol.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    private fun drawPhotoSection(context: Context, ctx: DrawContext, title: String, urls: List<String>) {
        if (urls.isEmpty()) return
        ctx.section(title)
        val thumbW = 150f
        val thumbH = 110f
        val gap = 12f
        var x = MARGIN
        ctx.ensureSpace(thumbH + 16f)
        var rowTop = ctx.y
        for (url in urls) {
            if (x + thumbW > PAGE_WIDTH - MARGIN) {
                x = MARGIN
                rowTop += thumbH + gap
                ctx.y = rowTop
                ctx.ensureSpace(thumbH + 16f)
                rowTop = ctx.y
            }
            val bmp = downloadBitmap(context, url, thumbW.toInt(), thumbH.toInt())
            if (bmp != null) {
                val dst = android.graphics.RectF(x, rowTop, x + thumbW, rowTop + thumbH)
                ctx.canvas.drawBitmap(bmp, null, dst, null)
                ctx.canvas.drawRect(dst, ctx.linePaint)
            }
            x += thumbW + gap
        }
        ctx.y = rowTop + thumbH + 16f
    }

    private fun downloadBitmap(context: Context, url: String, w: Int, h: Int): Bitmap? {
        return try {
            Glide.with(context).asBitmap().load(url).submit(w, h).get()
        } catch (e: Exception) {
            null
        }
    }

    private fun traduzStatus(status: String): String = when (status) {
        "completed" -> "Concluído"
        "in_progress" -> "Em atendimento"
        "assigned" -> "Atribuído"
        "distributing" -> "Em distribuição"
        "cancelled" -> "Cancelado"
        else -> status
    }

    private fun traduzResolucao(r: String): String = when (r) {
        "resolved" -> "Resolvido"
        "return_needed" -> "Necessita retorno"
        "not_resolved" -> "Não resolvido"
        else -> "—"
    }

    /** Estado mutável de desenho com paginação automática. */
    private class DrawContext(
        val doc: PdfDocument,
        val titlePaint: Paint,
        val sectionPaint: Paint,
        val labelPaint: Paint,
        val valuePaint: Paint,
        val linePaint: Paint
    ) {
        lateinit var page: PdfDocument.Page
        lateinit var canvas: android.graphics.Canvas
        var y = MARGIN
        private var pageNumber = 0

        fun newPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = doc.startPage(info)
            canvas = page.canvas
            y = MARGIN
        }

        fun finishPage() { doc.finishPage(page) }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                finishPage(); newPage()
            }
        }

        fun hr() {
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 14f
        }

        fun section(name: String) {
            ensureSpace(30f)
            y += 8f
            canvas.drawText(name, MARGIN, y, sectionPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 16f
        }

        fun row(label: String, value: String) {
            if (value.isBlank()) { canvas.drawText(label, MARGIN, y, labelPaint); y += 15f; return }
            ensureSpace(16f)
            canvas.drawText("$label:", MARGIN, y, labelPaint)
            val labelWidth = labelPaint.measureText("$label:") + 8f
            // Quebra o valor se for muito largo
            val maxWidth = PAGE_WIDTH - MARGIN - (MARGIN + labelWidth)
            val lines = wrap(value, valuePaint, maxWidth)
            lines.forEachIndexed { i, line ->
                if (i > 0) { y += 14f; ensureSpace(16f) }
                canvas.drawText(line, MARGIN + labelWidth, y, valuePaint)
            }
            y += 16f
        }

        fun boolRow(label: String, value: Boolean?) {
            val txt = when (value) { true -> "Sim"; false -> "Não"; null -> "—" }
            row(label, txt)
        }

        fun paragraph(text: String) {
            val maxWidth = PAGE_WIDTH - 2 * MARGIN
            wrap(text, labelPaint, maxWidth).forEach { line ->
                ensureSpace(15f)
                canvas.drawText(line, MARGIN, y, labelPaint)
                y += 14f
            }
            y += 6f
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (paint.measureText(text) <= maxWidth) return listOf(text)
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                    lines.add(current.toString()); current = StringBuilder(word)
                } else current = StringBuilder(candidate)
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            return lines
        }
    }
}
