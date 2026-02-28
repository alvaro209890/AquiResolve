package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.loginapp.databinding.ActivityTermsOfServiceBinding

class TermsOfServiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsOfServiceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsOfServiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupContent()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCancellationPolicy.setOnClickListener {
            startActivity(Intent(this, CancellationRefundActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Termos de Uso"
        }
    }

    private fun setupContent() {
        binding.tvTermsContent.text = getTermsOfServiceContent()
    }

    private fun getTermsOfServiceContent(): String {
        return """
            TERMOS DE USO DO CLIENTE – PLATAFORMA AQUI RESOLVE
            
            Última atualização: 20 de janeiro de 2026
            
            Plataforma: Aqui Resolve
            Razão Social: Aqui Resolve Serviços Digitais Ltda.
            CNPJ: 43.246.176/0001-30
            E-mail: aquiresolveservico123@gmail.com
            Sede: Vitória – Espírito Santo – Brasil
            
            1. OBJETO
            Estes Termos regulam o uso da plataforma Aqui Resolve pelo CLIENTE, pessoa física ou jurídica que solicita serviços por meio do aplicativo ou site.
            A plataforma disponibiliza infraestrutura tecnológica de intermediação, conectando clientes a prestadores de serviços autônomos e independentes.
            
            2. ACEITAÇÃO
            Ao utilizar a plataforma, o cliente declara que:
            • leu e compreendeu estes Termos;
            • concorda com todas as suas disposições;
            • compromete-se a utilizá-la conforme a lei e a boa-fé.
            Caso não concorde, deverá interromper o uso.
            
            3. PAPEL DA PLATAFORMA (DELIMITAÇÃO REALISTA)
            A Aqui Resolve:
            • não executa serviços;
            • não substitui o prestador na obrigação técnica;
            • não garante resultado técnico específico;
            • integra a cadeia de consumo apenas quanto à intermediação digital.
            A responsabilidade da plataforma limita-se às atividades de:
            • intermediação tecnológica;
            • processamento e gestão de pagamentos sob sua custódia;
            • suporte administrativo.
            
            4. CADASTRO DO CLIENTE
            O cliente é responsável por:
            • fornecer informações verdadeiras e atualizadas;
            • manter a confidencialidade de sua conta;
            • todas as solicitações realizadas por seu cadastro.
            O uso indevido da conta é de responsabilidade exclusiva do cliente.
            
            5. SOLICITAÇÃO E CONTRATAÇÃO DO SERVIÇO
            Ao solicitar um serviço, o cliente reconhece que:
            • o serviço será executado por prestador autônomo;
            • os detalhes técnicos da execução são de responsabilidade do prestador;
            • a contratação se formaliza com o aceite do prestador.
            
            6. EXECUÇÃO DO SERVIÇO
            A execução do serviço é obrigação exclusiva do prestador.
            A plataforma:
            • não interfere na forma técnica de execução;
            • não supervisiona presencialmente o serviço;
            • não garante adequação do serviço a expectativas subjetivas do cliente.
            
            7. PAGAMENTOS
            Os pagamentos podem ser realizados por meios disponibilizados na plataforma.
            A Aqui Resolve:
            • responde por valores sob sua custódia;
            • atua para resolver falhas operacionais internas comprovadas;
            • não responde por falhas externas de instituições financeiras, sem prejuízo do dever de suporte.
            
            8. CÓDIGO DE VERIFICAÇÃO
            A confirmação por código:
            • indica que o serviço foi executado de forma aparente;
            • autoriza a liberação do pagamento ao prestador.
            O fornecimento do código não elimina o direito do cliente de reclamar:
            • vícios ocultos;
            • defeitos identificados posteriormente, nos prazos legais.
            
            9. CANCELAMENTO E REEMBOLSO
            Os cancelamentos seguem a Política de Cancelamento e Reembolso, parte integrante destes Termos.
            Poderá ser aplicada taxa compensatória, desde que:
            • previamente informada;
            • proporcional aos custos operacionais e deslocamento;
            • limitada ao efetivo prejuízo causado.
            Os prazos de reembolso dependem do meio de pagamento utilizado.
            
            10. RESPONSABILIDADE DA PLATAFORMA
            A plataforma responde:
            • por falhas na intermediação tecnológica;
            • por erros comprovados no processamento de pagamentos sob sua gestão.
            A plataforma não responde por:
            • erro técnico do serviço;
            • má execução pelo prestador;
            • danos causados exclusivamente pelo prestador, salvo quando comprovada falha direta da intermediação.
            
            11. MEDIAÇÃO DE CONFLITOS
            A Aqui Resolve poderá atuar como facilitadora administrativa, buscando solução razoável entre as partes, sem substituir o Poder Judiciário.
            
            12. AVALIAÇÕES
            O cliente poderá avaliar o prestador de forma honesta e objetiva.
            É vedado:
            • conteúdo ofensivo;
            • informações falsas;
            • avaliações abusivas.
            A plataforma poderá moderar conteúdos ilegais ou inadequados.
            
            13. USO INDEVIDO
            É proibido ao cliente:
            • utilizar a plataforma para fins ilícitos;
            • fornecer informações falsas;
            • constranger ou ameaçar prestadores;
            • tentar burlar regras de pagamento.
            
            14. SUSPENSÃO OU ENCERRAMENTO
            A plataforma poderá suspender ou encerrar contas em caso de violação destes Termos, garantindo, sempre que possível, motivação e proporcionalidade.
            
            15. PROTEÇÃO DE DADOS (LGPD)
            Os dados pessoais são tratados conforme a Lei nº 13.709/2018, nos termos da Política de Privacidade da plataforma.
            
            16. LEI APLICÁVEL E FORO
            Este Termo é regido pelas leis da República Federativa do Brasil.
            Fica assegurado ao cliente o direito de ajuizar ação no foro de seu domicílio, conforme o Código de Defesa do Consumidor.
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

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}




