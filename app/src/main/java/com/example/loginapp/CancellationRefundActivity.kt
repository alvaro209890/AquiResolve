package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.loginapp.databinding.ActivityCancellationRefundBinding

/**
 * Activity que exibe a Política de Cancelamento e Reembolso da plataforma Aqui Resolve.
 */
class CancellationRefundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancellationRefundBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCancellationRefundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupContent()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Política de Cancelamento e Reembolso"
        }
    }

    private fun setupContent() {
        binding.tvPolicyContent.text = getCancellationRefundContent()
    }

    private fun getCancellationRefundContent(): String {
        return """
            POLÍTICA DE CANCELAMENTO E REEMBOLSO
            PLATAFORMA AQUI RESOLVE
            
            Última atualização: 20 de janeiro de 2026
            
            Plataforma: Aqui Resolve
            Razão Social: Aqui Resolve Serviços Digitais Ltda.
            CNPJ: 43.246.176/0001-30
            E-mail: aquiresolveservico123@gmail.com
            Sede: Vitória – Espírito Santo – Brasil
            
            Esta Política integra e complementa os Termos de Uso do Cliente da plataforma Aqui Resolve.
            
            1. OBJETIVO DA POLÍTICA
            Esta Política estabelece:
            • regras de cancelamento de serviços;
            • condições de reembolso;
            • aplicação de taxas compensatórias;
            • prazos e formas de devolução de valores.
            Tudo em conformidade com o Código de Defesa do Consumidor (CDC) e a boa-fé objetiva.
            
            2. DEFINIÇÕES
            Para fins desta Política:
            • Cliente: usuário que solicita serviços.
            • Prestador: profissional autônomo que executa o serviço.
            • Plataforma: Aqui Resolve.
            • Aceite do serviço: momento em que o prestador confirma a execução.
            • Deslocamento: início comprovado do trajeto até o local do serviço.
            
            3. CANCELAMENTO PELO CLIENTE
            
            3.1 Antes do aceite do prestador
            • Cancelamento gratuito.
            • Reembolso integral, se houver pagamento antecipado.
            
            3.2 Após o aceite e antes do deslocamento
            Poderá ser aplicada taxa compensatória limitada, destinada a cobrir:
            • custos operacionais da plataforma;
            • tempo reservado pelo prestador.
            Limite da taxa: até 10% do valor do serviço.
            
            3.3 Após o início do deslocamento
            Poderá ser aplicada taxa compensatória proporcional a:
            • deslocamento comprovado;
            • tempo efetivamente reservado;
            • custos operacionais.
            Limite da taxa: até 30% do valor do serviço.
            A taxa não pode ultrapassar o prejuízo efetivamente comprovado, sob pena de nulidade.
            
            3.4 Após o início da execução
            • Não há reembolso do valor referente ao serviço já executado.
            • Valores de materiais não utilizados poderão ser reembolsados, quando aplicável.
            
            4. CANCELAMENTO PELO PRESTADOR
            Se o prestador cancelar:
            • o cliente terá reembolso integral;
            • a plataforma poderá aplicar medidas administrativas ao prestador.
            
            5. CANCELAMENTO POR FALHA DA PLATAFORMA
            Em caso de falha comprovada da plataforma:
            • o cliente terá reembolso integral;
            • eventuais taxas serão automaticamente canceladas.
            
            6. NÃO COMPARECIMENTO DO CLIENTE
            Caso o cliente:
            • não esteja presente no local;
            • forneça endereço incorreto;
            • não responda tentativas razoáveis de contato;
            Poderá ser aplicada taxa compensatória de até 30%, mediante comprovação do deslocamento.
            
            7. REEMBOLSO – FORMAS E PRAZOS
            Os reembolsos seguem o meio de pagamento utilizado:
            • Pix: até 5 dias úteis
            • Cartão de crédito: conforme regras da operadora (1 a 2 faturas)
            • Carteira digital: até 7 dias úteis
            Os prazos são estimados e dependem de terceiros (instituições financeiras).
            
            8. SERVIÇOS COM VÍCIO OU DEFEITO
            Em caso de:
            • vício oculto;
            • defeito comprovado;
            O cliente poderá solicitar:
            • reexecução do serviço, quando possível; ou
            • reembolso proporcional, conforme análise administrativa.
            Nada nesta Política limita os direitos previstos no CDC.
            
            9. TAXA COMPENSATÓRIA – NATUREZA JURÍDICA
            A taxa:
            • não possui natureza punitiva;
            • visa recompor prejuízo efetivo;
            • é aplicada com base na proporcionalidade.
            Fundamento legal: Art. 413 do Código Civil + CDC (boa-fé e equilíbrio contratual).
            
            10. MEDIAÇÃO ADMINISTRATIVA
            A plataforma poderá atuar como facilitadora administrativa, buscando solução razoável, sem obrigação de resultado e sem afastar o direito de ação judicial.
            
            11. ABUSO OU FRAUDE
            A plataforma poderá:
            • negar reembolsos em casos de fraude comprovada;
            • suspender contas em caso de abuso recorrente.
            
            12. DISPOSIÇÕES FINAIS
            Esta Política:
            • integra os Termos de Uso do Cliente;
            • prevalece sobre comunicações informais;
            • poderá ser atualizada, com aviso prévio na plataforma.
        """.trimIndent()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
