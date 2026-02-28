
package com.example.loginapp

import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * Gerenciador de autenticação local
 * 
 * Esta classe simula o comportamento do Firebase Authentication
 * sem depender de serviços externos. Ideal para desenvolvimento
 * e testes antes da integração com banco de dados.
 */
object LocalAuthManager {
    
    // Dados simulados de usuários (em produção, isso viria do banco)
    private val mockUsers = mutableMapOf<String, UserData>()
    private val mockProviders = mutableMapOf<String, ProviderData>()
    
    // Usuário atual logado
    var currentUser: UserData? = null
        private set
    var currentProvider: ProviderData? = null
        private set

    /**
     * Dados do usuário cliente
     */
    data class UserData(
        val id: String,
        val email: String,
        val fullName: String,
        val password: String,
        val userType: String = "client",
        val cpf: String? = null,
        val phone: String? = null,
        val cep: String? = null,
        val address: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Dados do prestador de serviço
     */
    data class ProviderData(
        val id: String,
        val email: String,
        val fullName: String,
        val password: String,
        val cpf: String,
        val phone: String,
        val cep: String,
        val address: String,
        val services: List<String>,
        val bank: String? = null, // Opcional - será preenchido posteriormente
        val agency: String? = null, // Opcional - será preenchido posteriormente
        val account: String? = null, // Opcional - será preenchido posteriormente
        val isVerified: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Resultado da autenticação
     */
    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
    
    /**
     * Verifica se há um usuário logado
     */
    val isUserLoggedIn: Boolean
        get() = currentUser != null || currentProvider != null
    
    /**
     * Retorna o usuário atual
     */
    val user: UserData?
        get() = currentUser
    
    /**
     * Retorna o prestador atual
     */
    val provider: ProviderData?
        get() = currentProvider
    
    /**
     * Verifica se o usuário atual é um prestador
     */
    val isCurrentUserProvider: Boolean
        get() = currentProvider != null
    
    /**
     * Faz login com email e senha
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        // Simular delay de rede
        delay(1000)
        clearSession()
        val normalizedEmail = normalizeEmail(email)
        
        // Verificar se o email é válido
        if (normalizedEmail.isEmpty() || !normalizedEmail.contains("@")) {
            return AuthResult.Error("Email inválido")
        }
        
        // Verificar se a senha tem pelo menos 1 caractere
        if (password.isEmpty()) {
            return AuthResult.Error("Senha é obrigatória")
        }
        
        // Primeiro verificar se é um prestador
        val existingProvider = mockProviders[normalizedEmail]
        if (existingProvider != null) {
            return if (passwordMatches(password, existingProvider.password)) {
                currentProvider = existingProvider
                AuthResult.Success
            } else {
                AuthResult.Error("Senha incorreta")
            }
        }
        
        // Depois verificar se é um usuário cliente
        val existingUser = mockUsers[normalizedEmail] ?: return AuthResult.Error("Usuário não encontrado")

        return if (passwordMatches(password, existingUser.password)) {
            currentUser = existingUser
            AuthResult.Success
        } else {
            AuthResult.Error("Senha incorreta")
        }
    }
    
    /**
     * Cria uma nova conta de cliente
     */
    suspend fun createUserWithEmailAndPassword(
        email: String, 
        password: String, 
        fullName: String
    ): AuthResult {
        // Simular delay de rede
        delay(1500)
        val normalizedEmail = normalizeEmail(email)
        
        return when {
            normalizedEmail.isEmpty() || !normalizedEmail.contains("@") -> AuthResult.Error("Email inválido")
            password.length < 6 -> AuthResult.Error("Senha deve ter pelo menos 6 caracteres")
            fullName.isEmpty() -> AuthResult.Error("Nome é obrigatório")
            mockUsers.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso")
            mockProviders.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso por um prestador")
            else -> {
                val newUser = UserData(
                    id = "user_${System.currentTimeMillis()}",
                    email = normalizedEmail,
                    fullName = fullName,
                    password = hashPassword(password)
                )
                
                mockUsers[normalizedEmail] = newUser
                currentUser = newUser
                currentProvider = null
                AuthResult.Success
            }
        }
    }
    
    /**
     * Cria uma nova conta de prestador
     */
    suspend fun createProviderAccount(
        fullName: String,
        cpf: String,
        email: String,
        phone: String,
        password: String,
        cep: String,
        address: String,
        services: List<String>,
        bank: String,
        agency: String,
        account: String
    ): AuthResult {
        // Simular delay de rede
        delay(2000)
        val normalizedEmail = normalizeEmail(email)
        
        return when {
            normalizedEmail.isEmpty() || !normalizedEmail.contains("@") -> AuthResult.Error("Email inválido")
            fullName.isEmpty() -> AuthResult.Error("Nome é obrigatório")
            password.length < 6 -> AuthResult.Error("Senha deve ter pelo menos 6 caracteres")
            cpf.isEmpty() -> AuthResult.Error("CPF é obrigatório")
            phone.isEmpty() -> AuthResult.Error("Telefone é obrigatório")
            cep.isEmpty() -> AuthResult.Error("CEP é obrigatório")
            address.isEmpty() -> AuthResult.Error("Endereço é obrigatório")
            services.isEmpty() -> AuthResult.Error("Selecione pelo menos um serviço")
            // Campos de banco são opcionais - serão preenchidos posteriormente
            mockUsers.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso por um cliente")
            mockProviders.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso")
            mockProviders.values.any { it.cpf == cpf } -> AuthResult.Error("CPF já cadastrado")
            else -> {
                val newProvider = ProviderData(
                    id = "provider_${System.currentTimeMillis()}",
                    email = normalizedEmail,
                    fullName = fullName,
                    password = hashPassword(password),
                    cpf = cpf,
                    phone = phone,
                    cep = cep,
                    address = address,
                    services = services,
                    bank = if (bank.isNotEmpty()) bank else null,
                    agency = if (agency.isNotEmpty()) agency else null,
                    account = if (account.isNotEmpty()) account else null,
                    isVerified = false
                )
                
                mockProviders[normalizedEmail] = newProvider
                currentProvider = newProvider
                currentUser = null
                AuthResult.Success
            }
        }
    }
    
    /**
     * Cria uma nova conta de cliente
     */
    suspend fun createClientAccount(
        fullName: String,
        cpf: String,
        email: String,
        phone: String,
        password: String,
        cep: String,
        address: String
    ): AuthResult {
        // Simular delay de rede
        delay(1500)
        val normalizedEmail = normalizeEmail(email)
        
        return when {
            normalizedEmail.isEmpty() || !normalizedEmail.contains("@") -> AuthResult.Error("Email inválido")
            fullName.isEmpty() -> AuthResult.Error("Nome é obrigatório")
            password.length < 6 -> AuthResult.Error("Senha deve ter pelo menos 6 caracteres")
            phone.isEmpty() -> AuthResult.Error("Telefone é obrigatório")
            cep.isEmpty() -> AuthResult.Error("CEP é obrigatório")
            address.isEmpty() -> AuthResult.Error("Endereço é obrigatório")
            mockUsers.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso")
            mockProviders.containsKey(normalizedEmail) -> AuthResult.Error("Email já está em uso por um prestador")
            cpf.isNotEmpty() && mockUsers.values.any { it.cpf == cpf } -> AuthResult.Error("CPF já cadastrado")
            else -> {
                val newUser = UserData(
                    id = "client_${System.currentTimeMillis()}",
                    email = normalizedEmail,
                    fullName = fullName,
                    password = hashPassword(password),
                    cpf = if (cpf.isNotEmpty()) cpf else null,
                    phone = phone,
                    cep = cep,
                    address = address
                )
                
                mockUsers[normalizedEmail] = newUser
                currentUser = newUser
                currentProvider = null
                AuthResult.Success
            }
        }
    }
    
    /**
     * Faz logout
     */
    fun signOut() {
        clearSession()
    }
    
    /**
     * Envia email de recuperação de senha (simulado)
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        // Simular delay de rede
        delay(800)
        
        // DESENVOLVIMENTO: Aceitar qualquer email válido
        return if (email.isNotEmpty() && email.contains("@")) {
            AuthResult.Success
        } else {
            AuthResult.Error("Email inválido")
        }
    }
    
    /**
     * Simula login com Google
     */
    suspend fun signInWithGoogle(): AuthResult {
        // Simular delay de rede
        delay(1200)
        
        // Simular sucesso ocasional (70% de chance)
        return if (System.currentTimeMillis() % 10 < 7) {
            val googleUser = UserData(
                id = "google_${System.currentTimeMillis()}",
                email = "usuario.google@gmail.com",
                fullName = "Usuário Google",
                password = ""
            )
            currentUser = googleUser
            AuthResult.Success
        } else {
            AuthResult.Error("Falha na autenticação com Google")
        }
    }
    
    /**
     * Simula login com Facebook
     */
    suspend fun signInWithFacebook(): AuthResult {
        // Simular delay de rede
        delay(1200)
        
        // Simular sucesso ocasional (60% de chance)
        return if (System.currentTimeMillis() % 10 < 6) {
            val facebookUser = UserData(
                id = "facebook_${System.currentTimeMillis()}",
                email = "usuario.facebook@facebook.com",
                fullName = "Usuário Facebook",
                password = ""
            )
            currentUser = facebookUser
            AuthResult.Success
        } else {
            AuthResult.Error("Falha na autenticação com Facebook")
        }
    }
    
    /**
     * Busca prestadores por região e serviço
     */
    suspend fun findProvidersByRegionAndService(cep: String, service: String): List<ProviderData> {
        // Simular delay de rede
        delay(500)
        
        return mockProviders.values.filter { provider ->
            // Simular busca por região (primeiros 5 dígitos do CEP)
            val providerCepPrefix = provider.cep.take(5)
            val searchCepPrefix = cep.take(5)
            
            providerCepPrefix == searchCepPrefix && 
            provider.services.contains(service) &&
            provider.isVerified
        }
    }
    
    /**
     * Busca todos os prestadores verificados
     */
    suspend fun getAllVerifiedProviders(): List<ProviderData> {
        // Simular delay de rede
        delay(300)
        
        return mockProviders.values.filter { it.isVerified }
    }
    
    /**
     * Obtém prestador por ID
     */
    fun getProviderById(id: String): ProviderData? {
        return mockProviders.values.find { it.id == id }
    }
    
    /**
     * Obtém prestador por email
     */
    fun getProviderByEmail(email: String): ProviderData? {
        return mockProviders[normalizeEmail(email)]
    }
    
    /**
     * Atualiza dados do prestador
     */
    suspend fun updateProvider(provider: ProviderData): AuthResult {
        delay(500)
        
        return if (mockProviders.containsKey(provider.email)) {
            mockProviders[provider.email] = provider
            if (currentProvider?.id == provider.id) {
                currentProvider = provider
            }
            AuthResult.Success
        } else {
            AuthResult.Error("Prestador não encontrado")
        }
    }
    
    /**
     * Verifica se o prestador está logado
     */
    fun isProviderLoggedIn(): Boolean {
        return currentProvider != null
    }
    
    /**
     * Obtém o prestador atual
     */
    fun getCurrentProviderData(): ProviderData? {
        return currentProvider
    }

    private fun clearSession() {
        currentUser = null
        currentProvider = null
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    private fun passwordMatches(rawPassword: String, storedPassword: String): Boolean {
        val hashedPassword = hashPassword(rawPassword)
        return storedPassword == rawPassword || storedPassword == hashedPassword
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

}
