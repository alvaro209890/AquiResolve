package com.example.loginapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loginapp.databinding.ActivityHelpSupportBinding

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpSupportBinding
    private var isProvider = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Verificar se é prestador ou cliente
        val userType = intent.getStringExtra("user_type")
        isProvider = userType == FirebaseAuthManager.USER_TYPE_PROVIDER

        setupToolbar()
        setupContent()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Ajuda e Suporte"
        }
    }

    private fun setupContent() {
        if (isProvider) {
            setupProviderFAQ()
        } else {
            setupClientFAQ()
        }
    }

    private fun setupProviderFAQ() {
        binding.tvTitle.text = "Perguntas Frequentes - Prestador"
        
        val faqContent = """
            ❓ COMO FUNCIONA A VERIFICAÇÃO DE PRESTADOR?
            
            Para aceitar pedidos, você precisa ser verificado pela administração. O processo inclui:
            • Envio de documentos (RG, CPF, comprovante de endereço)
            • Análise pela equipe administrativa
            • Aprovação ou rejeição (você será notificado)
            • Tempo médio de análise: 24-48 horas
            
            Após a aprovação, você poderá aceitar e executar pedidos normalmente.
            
            
            💰 COMO FUNCIONAM OS PAGAMENTOS?

            • Cada serviço possui um valor fixo que você recebe
            • Consulte a tabela de valores no seu perfil (aba Configurações)
            • O valor é creditado após a confirmação de conclusão do serviço
            • O valor aparece no seu saldo na tela inicial
            • Depósitos são realizados automaticamente toda quarta-feira via PIX
            • Certifique-se de ter sua chave PIX cadastrada corretamente
            
            
            📋 COMO ACEITAR UM PEDIDO?
            
            • Acesse a aba "Pedidos Disponíveis"
            • Visualize os detalhes do serviço solicitado
            • Clique em "Aceitar Pedido"
            • O cliente será notificado automaticamente
            • Você pode iniciar o serviço quando estiver pronto
            
            
            ✅ COMO FINALIZAR UM PEDIDO?
            
            • Após concluir o serviço, clique em "Finalizar Pedido"
            • Solicite o código de verificação ao cliente
            • O cliente fornecerá um código de 6 dígitos
            • Insira o código para confirmar a conclusão
            • Sua comissão será creditada automaticamente
            
            
            🔐 O QUE É O CÓDIGO DE VERIFICAÇÃO?
            
            O código de verificação é uma segurança para garantir que:
            • O serviço foi realizado completamente
            • Não houve danos à propriedade do cliente
            • O cliente está satisfeito com o serviço
            
            Sem o código, você não poderá finalizar o pedido e receber a comissão.
            
            
            💳 QUANDO RECEBO MEU PAGAMENTO?
            
            • Seu saldo é atualizado imediatamente após finalizar um pedido
            • Os depósitos são processados automaticamente toda quarta-feira
            • O valor será depositado na chave PIX cadastrada no seu perfil
            • Você receberá uma notificação quando o depósito for realizado
            
            
            📝 POSSO CANCELAR UM PEDIDO?
            
            Sim, mas com algumas condições:
            • Você pode cancelar antes de iniciar o serviço sem penalidades
            • Cancelamento após início pode resultar em penalização na reputação
            • Cancelamentos frequentes podem levar à suspensão da conta
            • O cliente será notificado automaticamente
            
            
            ⭐ COMO FUNCIONAM AS AVALIAÇÕES?
            
            • Clientes podem avaliar seu trabalho após a conclusão
            • Avaliações aparecem no seu perfil e afetam sua reputação
            • Você pode responder às avaliações
            • Mantenha um bom histórico para receber mais pedidos
            
            
            🔄 O QUE FAZER SE O CLIENTE NÃO FORNECER O CÓDIGO?
            
            • Entre em contato com o cliente através do chat
            • Explique a importância do código para finalização
            • Se houver problemas, entre em contato com o suporte
            • Não finalize o serviço sem o código
            
            
            📱 COMO ATUALIZAR MEUS DADOS?
            
            • Acesse o menu "Perfil"
            • Você pode atualizar:
            - Foto de perfil
            - Nome de usuário
            - Chave PIX (importante para receber pagamentos)
            - Dados bancários
            
            
            🚫 O QUE FAZER SE MEU PEDIDO FOR CANCELADO PELO CLIENTE?
            
            • Você será notificado automaticamente
            • Se o serviço já foi iniciado, entre em contato com o suporte
            • Pedidos cancelados antes do início não afetam sua reputação
            
            
            ⚠️ PRECISO TER SEGURO OU LICENÇAS?
            
            • Depende do tipo de serviço que você oferece
            • Alguns serviços podem exigir certificações específicas
            • Verifique os requisitos legais para seu tipo de serviço
            • Mantenha documentação atualizada
            
            
            📞 PRECISO DE MAIS AJUDA?
            
            Entre em contato com nosso suporte através do email abaixo.
        """.trimIndent()
        
        binding.tvFaqContent.text = faqContent
    }

    private fun setupClientFAQ() {
        binding.tvTitle.text = "Perguntas Frequentes - Cliente"
        
        val faqContent = """
            📋 COMO CRIAR UM PEDIDO?
            
            • Acesse a tela inicial e clique em "Criar Pedido"
            • Selecione o tipo de serviço necessário
            • Preencha os detalhes (descrição, endereço, data preferencial)
            • Escolha entre preço fixo ou solicitar orçamentos
            • Efetue o pagamento para confirmar o pedido
            
            
            💰 COMO FUNCIONA O PAGAMENTO?
            
            • O pagamento é processado ao criar o pedido
            • Aceitamos cartão de crédito e PIX
            • O valor fica em garantia até a conclusão do serviço
            • Após a conclusão, o pagamento é liberado para o prestador
            
            
            🔐 O QUE É O CÓDIGO DE VERIFICAÇÃO?
            
            Você recebe um código único quando seu pedido é aceito:
            • Guarde este código com segurança
            • Forneça-o ao prestador apenas após verificar que:
            - O serviço foi realizado completamente
            - Não houve danos à sua residência
            - Você está satisfeito com o resultado
            • Ao fornecer o código, você confirma a conclusão do serviço
            
            
            ✅ COMO AVALIAR UM PRESTADOR?
            
            • Após a conclusão do serviço, você pode avaliar o prestador
            • Dê uma nota de 1 a 5 estrelas
            • Deixe um comentário sobre o serviço
            • Avaliações ajudam outros clientes a escolherem prestadores
            
            
            ❌ COMO CANCELAR UM PEDIDO?
            
            • Acesse os detalhes do pedido
            • Clique em "Cancelar Pedido"
            • Confirme o cancelamento
            • O valor pago será reembolsado automaticamente em até 24 horas
            • Você receberá uma notificação quando o reembolso for processado
            
            
            💳 QUANDO RECEBO O REEMBOLSO?
            
            • Quando você cancela um pedido, o reembolso é processado automaticamente
            • O valor retorna na mesma forma de pagamento utilizada
            • Prazo máximo: 24 horas após o cancelamento
            • Você receberá uma notificação quando o reembolso for processado
            
            ⚠️ IMPORTANTE SOBRE REEMBOLSO:
            
            • Você terá direito ao reembolso integral apenas se cancelar o pedido antes de completar 5 minutos após a confirmação do pagamento
            • Após 5 minutos, será deduzido R$ 35,00 do valor total pago pelo serviço
            • O valor restante será reembolsado automaticamente em até 24 horas
            
            
            📞 COMO ENTRAR EM CONTATO COM O PRESTADOR?
            
            • Acesse os detalhes do pedido
            • Clique em "Chat com Prestador"
            • Você pode conversar diretamente com o prestador
            • Use o chat para esclarecer dúvidas e combinar detalhes
            
            
            ⏰ QUANTO TEMPO LEVA PARA UM PRESTADOR ACEITAR MEU PEDIDO?
            
            • Depende da disponibilidade de prestadores na sua região
            • Pedidos urgentes podem ser aceitos em minutos
            • Pedidos normais geralmente são aceitos em algumas horas
            • Você receberá uma notificação quando um prestador aceitar
            
            
            🔄 O QUE FAZER SE O PRESTADOR CANCELAR?
            
            • Você será notificado automaticamente
            • O pagamento será reembolsado integralmente
            • Você pode criar um novo pedido imediatamente
            • Outros prestadores podem aceitar seu pedido
            
            
            ⚠️ O QUE FAZER SE O SERVIÇO NÃO FOR REALIZADO CORRETAMENTE?
            
            • Não forneça o código de verificação
            • Entre em contato com o prestador através do chat
            • Se necessário, entre em contato com o suporte
            • Você tem até 48 horas após a conclusão para reportar problemas
            
            
            📝 POSSO SOLICITAR ORÇAMENTOS?
            
            Sim! Para serviços complexos:
            • Escolha "Solicitar Orçamentos" ao criar o pedido
            • Vários prestadores podem enviar cotações
            • Compare preços, prazos e avaliações
            • Escolha a melhor proposta para você
            
            
            🏠 O PRESTADOR PODE CAUSAR DANOS À MINHA PROPRIEDADE?
            
            • Prestadores são responsáveis por qualquer dano causado
            • Verifique o serviço antes de fornecer o código
            • Tire fotos se necessário como evidência
            • Entre em contato com o suporte em caso de problemas
            
            
            📱 COMO ATUALIZAR MEUS DADOS?
            
            • Acesse o menu "Perfil"
            • Você pode atualizar:
            - Foto de perfil
            - Nome de usuário
            - Email e telefone
            - Endereço
            
            
            🔔 COMO RECEBO NOTIFICAÇÕES?
            
            • Você recebe notificações automáticas sobre:
            - Quando um prestador aceita seu pedido
            - Quando o prestador inicia o serviço
            - Quando o prestador solicita o código
            - Sobre cancelamentos e reembolsos
            
            
            📞 PRECISO DE MAIS AJUDA?
            
            Entre em contato com nosso suporte através do email abaixo.
        """.trimIndent()
        
        binding.tvFaqContent.text = faqContent
    }

    private fun setupClickListeners() {
        binding.btnContactSupport.setOnClickListener {
            openEmailSupport()
        }
    }

    private fun openEmailSupport() {
        val email = "aquiresolveservico123@gmail.com"
        val subject = if (isProvider) {
            "Suporte - Prestador de Serviços"
        } else {
            "Suporte - Cliente"
        }
        
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Copiar email para área de transferência se não houver app de email
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Email de Suporte", email)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "Email copiado: $email", android.widget.Toast.LENGTH_LONG).show()
        }
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



