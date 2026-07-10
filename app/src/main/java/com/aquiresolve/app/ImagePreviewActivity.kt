package com.aquiresolve.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.aquiresolve.app.adapters.ImagePreviewAdapter
import com.aquiresolve.app.databinding.ActivityImagePreviewBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * ImagePreviewActivity - Visualização em tela cheia de imagens
 * 
 * Funcionalidades:
 * - Visualização em tela cheia
 * - Navegação entre imagens
 * - Zoom e pan
 * - Informações da imagem
 */
class ImagePreviewActivity : AppCompatActivity(), StatusBarStripColor {

    override val statusBarStripColorRes: Int get() = R.color.black


    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityImagePreviewBinding
    
    // Adapter para o ViewPager
    private lateinit var adapter: ImagePreviewAdapter
    
    // Lista de imagens
    private var images = listOf<ImagePreviewAdapter.ImageItem>()
    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ViewBinding
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
        loadImages()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Barra de status: cor sólida do tema (Android <15) / faixa do EdgeToEdgeInsets (15+).
        // O hack fullscreen antigo deixava o conteúdo sob a barra de status sem compensação.
        
        // Configurar ViewPager
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // Configurar TabLayout
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
            // Não precisamos configurar nada aqui
        }.attach()
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão compartilhar
        binding.btnShare.setOnClickListener {
            shareCurrentImage()
        }
        
        // Botão informações
        binding.btnInfo.setOnClickListener {
            showImageInfo()
        }
        
        // Listener do ViewPager
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateImageInfo()
            }
        })
    }

    /**
     * Carrega as imagens da intent
     */
    private fun loadImages() {
        // Obter URIs da intent
        val uris = intent.getStringArrayListExtra("image_uris")
        val positions = intent.getIntegerArrayListExtra("image_positions")
        val fileNames = intent.getStringArrayListExtra("file_names")
        val fileSizes = intent.getLongArrayExtra("file_sizes")
        val types = intent.getStringArrayListExtra("image_types")
        
        if (uris.isNullOrEmpty()) {
            showToast("Nenhuma imagem encontrada")
            finish()
            return
        }
        
        // Criar lista de imagens
        images = uris.mapIndexed { index, uriString ->
            ImagePreviewAdapter.ImageItem(
                uri = Uri.parse(uriString),
                fileName = fileNames?.getOrNull(index),
                fileSize = fileSizes?.getOrNull(index) ?: 0L,
                type = types?.getOrNull(index) ?: "GENERAL"
            )
        }
        
        // Definir posição inicial
        currentPosition = intent.getIntExtra("current_position", 0)
        if (currentPosition >= images.size) {
            currentPosition = 0
        }
        
        // Configurar adapter
        adapter = ImagePreviewAdapter(images)
        binding.viewPager.adapter = adapter
        
        // Ir para a posição inicial
        binding.viewPager.setCurrentItem(currentPosition, false)
        
        // Atualizar informações
        updateImageInfo()
    }

    /**
     * Atualiza as informações da imagem atual
     */
    private fun updateImageInfo() {
        if (currentPosition < images.size) {
            val image = images[currentPosition]
            
            // Atualizar nome do arquivo
            binding.tvFileName.text = image.fileName ?: "Imagem ${currentPosition + 1}"
            
            // Atualizar tamanho do arquivo
            binding.tvFileSize.text = formatFileSize(image.fileSize)
            
            // Atualizar tipo
            binding.tvImageType.text = getTypeDisplayName(image.type)
            
            // Atualizar contador
            binding.tvImageCounter.text = "${currentPosition + 1} de ${images.size}"
        }
    }

    /**
     * Compartilha a imagem atual
     */
    private fun shareCurrentImage() {
        if (currentPosition < images.size) {
            val image = images[currentPosition]
            
            try {
                val intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, image.uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, "Compartilhando imagem: ${image.fileName}")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(android.content.Intent.createChooser(intent, "Compartilhar imagem"))
                
            } catch (e: Exception) {
                showToast("Erro ao compartilhar imagem")
            }
        }
    }

    /**
     * Mostra informações detalhadas da imagem
     */
    private fun showImageInfo() {
        if (currentPosition < images.size) {
            val image = images[currentPosition]
            
            val info = """
                📁 Nome: ${image.fileName ?: "Sem nome"}
                📏 Tamanho: ${formatFileSize(image.fileSize)}
                🏷️ Tipo: ${getTypeDisplayName(image.type)}
                📍 Posição: ${currentPosition + 1} de ${images.size}
                🔗 URI: ${image.uri}
            """.trimIndent()
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Informações da Imagem")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Formata o tamanho do arquivo
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> "${(size / 1024.0).toInt()} KB"
            else -> "${(size / (1024.0 * 1024.0)).toInt()} MB"
        }
    }

    /**
     * Obtém nome de exibição do tipo
     */
    private fun getTypeDisplayName(type: String): String {
        return when (type.uppercase()) {
            "RG" -> "Documento de Identidade"
            "CPF" -> "CPF"
            "ADDRESS" -> "Comprovante de Endereço"
            "SERVICE" -> "Imagem do Serviço"
            else -> "Imagem Geral"
        }
    }

    /**
     * Mostra uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 