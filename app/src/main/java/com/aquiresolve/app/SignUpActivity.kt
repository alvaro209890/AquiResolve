package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivitySignupBinding
import kotlinx.coroutines.launch

/**
 * SignUpActivity - Tela de cadastro do aplicativo
 * 
 * Esta activity gerencia o cadastro de novos usuários, incluindo:
 * - Validação de dados de cadastro
 * - Criação de conta local
 * - Verificação de email (simulada)
 * - Simulação de integração com redes sociais (Google, Facebook)
 */
class SignUpActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivitySignupBinding
    
    // Variáveis para controle de estado
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        
        // Inicializar ViewBinding
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Barra de status: cor sólida do tema (Android <15) / faixa do EdgeToEdgeInsets (15+).
        // O hack fullscreen antigo deixava o conteúdo sob a barra de status sem compensação.
        
        // Configurar foco inicial no campo de nome
        binding.etFullName.requestFocus()
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Botão de cadastro principal
        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }
        
        // Link "Entrar" (voltar para login)
        binding.tvSignIn.setOnClickListener {
            handleSignIn()
        }
    }

    /**
     * Executa o processo de cadastro com email e senha
     */
    private fun performSignUp() {
        // Verificar se já está processando um cadastro
        if (isLoading) return
        
        // Obter dados dos campos
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val termsAccepted = binding.cbTerms.isChecked
        
        // Limpar erros anteriores
        clearErrors()
        
        // Validar campos
        if (!validateInputs(fullName, email, password, confirmPassword, termsAccepted)) {
            return
        }
        
        // Mostrar estado de carregamento
        setLoadingState(true)
        
        // Criar conta localmente
        createLocalAccount(fullName, email, password)
    }

    /**
     * Valida os campos de entrada do usuário
     * 
     * @param fullName Nome completo inserido pelo usuário
     * @param email Email inserido pelo usuário
     * @param password Senha inserida pelo usuário
     * @param confirmPassword Confirmação de senha inserida pelo usuário
     * @param termsAccepted Se os termos foram aceitos
     * @return true se todos os campos são válidos, false caso contrário
     */
    private fun validateInputs(
        fullName: String, 
        email: String, 
        password: String, 
        confirmPassword: String, 
        termsAccepted: Boolean
    ): Boolean {
        var isValid = true
        
        // Validar nome completo
        if (fullName.isEmpty()) {
            binding.tilFullName.error = getString(R.string.full_name_required)
            isValid = false
        } else if (fullName.length < 2) {
            binding.tilFullName.error = "Nome deve ter pelo menos 2 caracteres"
            isValid = false
        }
        
        // Validar email
        when {
            email.isEmpty() -> {
                binding.tilEmail.error = getString(R.string.email_required)
                isValid = false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = getString(R.string.email_invalid)
                isValid = false
            }
        }
        
        // Validar senha
        when {
            password.isEmpty() -> {
                binding.tilPassword.error = getString(R.string.password_required)
                isValid = false
            }
            password.length < 6 -> {
                binding.tilPassword.error = getString(R.string.weak_password)
                isValid = false
            }
        }
        
        // Validar confirmação de senha
        when {
            confirmPassword.isEmpty() -> {
                binding.tilConfirmPassword.error = getString(R.string.confirm_password_required)
                isValid = false
            }
            password != confirmPassword -> {
                binding.tilConfirmPassword.error = getString(R.string.passwords_dont_match)
                isValid = false
            }
        }
        
        // Validar termos
        if (!termsAccepted) {
            showErrorMessage(getString(R.string.terms_required))
            isValid = false
        }
        
        return isValid
    }

    /**
     * Cria conta localmente
     * 
     * @param fullName Nome completo do usuário
     * @param email Email do usuário
     * @param password Senha do usuário
     */
    private fun createLocalAccount(fullName: String, email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Criar conta localmente
                val result = LocalAuthManager.createUserWithEmailAndPassword(email, password, fullName)
                
                setLoadingState(false)
                
                when (result) {
                    is LocalAuthManager.AuthResult.Success -> {
                        // Cadastro bem-sucedido
                        showSuccessMessage("✅ ${getString(R.string.signup_success)}")
                        showSuccessMessage("📧 ${getString(R.string.email_verification_sent)}")
                        
                        // Redirecionar para tela de login após 2 segundos
                        binding.root.postDelayed({
                            handleSignIn()
                        }, 2000)
                    }
                    is LocalAuthManager.AuthResult.Error -> {
                        handleSignUpError(result.message)
                    }
                }
                
            } catch (e: Exception) {
                setLoadingState(false)
                showErrorMessage("❌ ${getString(R.string.signup_error)}")
            }
        }
    }

    /**
     * Trata erros de cadastro
     * 
     * @param errorMessage Mensagem de erro
     */
    private fun handleSignUpError(errorMessage: String) {
        when {
            errorMessage.contains("já está em uso") -> {
                binding.tilEmail.error = getString(R.string.email_already_exists)
                showErrorMessage("❌ ${getString(R.string.email_already_exists)}")
                binding.etEmail.requestFocus()
            }
            errorMessage.contains("pelo menos 6 caracteres") -> {
                binding.tilPassword.error = getString(R.string.weak_password)
                showErrorMessage("❌ ${getString(R.string.weak_password)}")
                binding.etPassword.requestFocus()
            }
            errorMessage.contains("Email inválido") -> {
                binding.tilEmail.error = getString(R.string.email_invalid)
                showErrorMessage("❌ ${getString(R.string.email_invalid)}")
                binding.etEmail.requestFocus()
            }
            else -> {
                showErrorMessage("❌ $errorMessage")
            }
        }
    }

    /**
     * Limpa todos os erros dos campos
     */
    private fun clearErrors() {
        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    /**
     * Controla o estado de carregamento da interface
     * 
     * @param loading true para mostrar carregamento, false para esconder
     */
    private fun setLoadingState(loading: Boolean) {
        isLoading = loading
        
        // Atualizar botão de cadastro
        binding.btnSignUp.apply {
            isEnabled = !loading
            text = if (loading) "Cadastrando..." else getString(R.string.sign_up_button)
        }
        
        // Desabilitar outros botões durante o carregamento
        binding.tvSignIn.isEnabled = !loading
        binding.cbTerms.isEnabled = !loading
    }

    /**
     * Manipula o clique em "Entrar" (voltar para login)
     */
    private fun handleSignIn() {
        // Voltar para a tela de login
        finish()
    }

    /**
     * Exibe uma mensagem de sucesso com estilo específico
     * 
     * @param message Mensagem de sucesso a ser exibida
     */
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem de erro com estilo específico
     * 
     * @param message Mensagem de erro a ser exibida
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem toast para o usuário
     * 
     * @param message Mensagem a ser exibida
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Limpa os recursos quando a activity é destruída
     */
    override fun onDestroy() {
        super.onDestroy()
        // Limpar recursos se necessário
    }
}
