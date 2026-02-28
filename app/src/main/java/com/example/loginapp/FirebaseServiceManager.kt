package com.example.loginapp

import com.example.loginapp.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await

/**
 * Gerenciador de serviços para Firebase
 * 
 * Responsável por todas as operações relacionadas a serviços:
 * - Categorias de serviços
 * - Tipos de serviços
 * - Prestadores
 * - Avaliações
 * - Favoritos
 */
class FirebaseServiceManager {
    
    private val db = FirebaseFirestore.getInstance()
    
    // Collections
    private val categoriesCollection = db.collection("service_categories")
    private val serviceTypesCollection = db.collection("service_types")
    private val providersCollection = db.collection("service_providers")
    private val reviewsCollection = db.collection("provider_reviews")
    private val quotesCollection = db.collection("service_quotes")
    private val favoritesCollection = db.collection("user_favorites")
    
    // ==================== POPULAR DADOS DE EXEMPLO ====================
    
    /**
     * Popula o banco de dados com dados de exemplo
     */
    suspend fun populateSampleData() {
        try {
            android.util.Log.d("FirebaseServiceManager", "🔄 Populando dados de exemplo...")
            
            // Criar categorias
            val categories = createSampleCategories()
            for (category in categories) {
                categoriesCollection.add(category).await()
            }
            
            // Criar tipos de serviços
            val serviceTypes = createSampleServiceTypes()
            for (serviceType in serviceTypes) {
                serviceTypesCollection.add(serviceType).await()
            }
            
            // Criar prestadores
            val providers = createSampleProviders()
            for (provider in providers) {
                providersCollection.add(provider).await()
            }
            
            android.util.Log.d("FirebaseServiceManager", "✅ Dados de exemplo criados com sucesso!")
            
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "❌ Erro ao popular dados: ${e.message}")
        }
    }
    
    /**
     * Cria categorias de exemplo
     */
    private fun createSampleCategories(): List<ServiceCategory> {
        return listOf(
            ServiceCategory(
                name = "Limpeza",
                description = "Serviços de limpeza residencial e comercial",
                icon = "ic_cleaning",
                color = "#4CAF50",
                order = 1
            ),
            ServiceCategory(
                name = "Manutenção",
                description = "Reparos e manutenção geral",
                icon = "ic_tools",
                color = "#FF9800",
                order = 2
            ),
            ServiceCategory(
                name = "Elétrica",
                description = "Serviços elétricos e instalações",
                icon = "ic_electrician",
                color = "#FFC107",
                order = 3
            ),
            ServiceCategory(
                name = "Encanamento",
                description = "Serviços de encanamento para encanador",
                icon = "ic_plumber",
                color = "#2196F3",
                order = 4
            ),
            ServiceCategory(
                name = "Pintura",
                description = "Serviços de pintura e acabamento",
                icon = "ic_painter",
                color = "#9C27B0",
                order = 5
            ),
            ServiceCategory(
                name = "Jardinagem",
                description = "Cuidados com jardim e paisagismo",
                icon = "ic_gardening",
                color = "#8BC34A",
                order = 6
            ),
            ServiceCategory(
                name = "Mudanças",
                description = "Serviços de mudança e transporte",
                icon = "ic_moving",
                color = "#795548",
                order = 7
            ),
            ServiceCategory(
                name = "Tecnologia",
                description = "Suporte técnico e TI",
                icon = "ic_it",
                color = "#607D8B",
                order = 8
            )
        )
    }
    
    /**
     * Cria tipos de serviços de exemplo
     */
    private fun createSampleServiceTypes(): List<ServiceType> {
        return listOf(
            // Limpeza
            ServiceType(
                categoryId = "limpeza",
                name = "Limpeza Residencial",
                description = "Limpeza completa da casa incluindo quartos, banheiros, cozinha e áreas comuns",
                icon = "ic_cleaning",
                estimatedPrice = 80.0,
                estimatedTime = "2-3 horas",
                order = 1
            ),
            ServiceType(
                categoryId = "limpeza",
                name = "Limpeza de Escritório",
                description = "Limpeza profissional para escritórios e ambientes corporativos",
                icon = "ic_cleaning",
                estimatedPrice = 120.0,
                estimatedTime = "3-4 horas",
                order = 2
            ),
            ServiceType(
                categoryId = "limpeza",
                name = "Limpeza Pós-Obra",
                description = "Limpeza especializada após reformas e construções",
                icon = "ic_cleaning",
                isComplex = true,
                estimatedPrice = 200.0,
                estimatedTime = "4-6 horas",
                order = 3
            ),
            
            // Manutenção
            ServiceType(
                categoryId = "manutencao",
                name = "Reparo de Móveis",
                description = "Conserto e restauração de móveis e objetos",
                icon = "ic_carpentry",
                estimatedPrice = 60.0,
                estimatedTime = "1-2 horas",
                order = 1
            ),
            ServiceType(
                categoryId = "manutencao",
                name = "Instalação de Prateleiras",
                description = "Instalação e montagem de prateleiras e estantes",
                icon = "ic_carpentry",
                estimatedPrice = 40.0,
                estimatedTime = "1 hora",
                order = 2
            ),
            
            // Elétrica
            ServiceType(
                categoryId = "eletrica",
                name = "Instalação de Tomadas",
                description = "Instalação e reparo de tomadas e interruptores",
                icon = "ic_electrician",
                estimatedPrice = 50.0,
                estimatedTime = "1 hora",
                order = 1
            ),
            ServiceType(
                categoryId = "eletrica",
                name = "Instalação de Ventilador",
                description = "Instalação de ventiladores de teto e parede",
                icon = "ic_electrician",
                estimatedPrice = 80.0,
                estimatedTime = "2 horas",
                order = 2
            ),
            ServiceType(
                categoryId = "eletrica",
                name = "Quadro de Distribuição",
                description = "Instalação e manutenção de quadros elétricos",
                icon = "ic_electrician",
                isComplex = true,
                estimatedPrice = 150.0,
                estimatedTime = "3-4 horas",
                order = 3
            ),
            
            // Encanamento
            ServiceType(
                categoryId = "encanamento",
                name = "Desentupimento",
                description = "Desentupimento de pias, ralos e canos",
                icon = "ic_plumber",
                estimatedPrice = 70.0,
                estimatedTime = "1-2 horas",
                order = 1
            ),
            ServiceType(
                categoryId = "encanamento",
                name = "Reparo de Vazamentos",
                description = "Identificação e reparo de vazamentos",
                icon = "ic_plumber",
                estimatedPrice = 90.0,
                estimatedTime = "2-3 horas",
                order = 2
            ),
            ServiceType(
                categoryId = "encanamento",
                name = "Instalação de encanamento",
                description = "Instalação completa de sistema hidráulico",
                icon = "ic_plumber",
                isComplex = true,
                estimatedPrice = 300.0,
                estimatedTime = "1 dia",
                order = 3
            ),
            
            // Pintura
            ServiceType(
                categoryId = "pintura",
                name = "Pintura de Quarto",
                description = "Pintura completa de quarto incluindo teto e paredes",
                icon = "ic_painter",
                estimatedPrice = 200.0,
                estimatedTime = "1 dia",
                order = 1
            ),
            ServiceType(
                categoryId = "pintura",
                name = "Pintura de Fachada",
                description = "Pintura externa de casas e prédios",
                icon = "ic_painter",
                isComplex = true,
                estimatedPrice = 500.0,
                estimatedTime = "2-3 dias",
                order = 2
            ),
            
            // Jardinagem
            ServiceType(
                categoryId = "jardinagem",
                name = "Manutenção de Jardim",
                description = "Corte de grama, poda de plantas e manutenção geral",
                icon = "ic_gardening",
                estimatedPrice = 60.0,
                estimatedTime = "2-3 horas",
                order = 1
            ),
            ServiceType(
                categoryId = "jardinagem",
                name = "Paisagismo",
                description = "Projeto e execução de paisagismo",
                icon = "ic_gardening",
                isComplex = true,
                estimatedPrice = 800.0,
                estimatedTime = "1 semana",
                order = 2
            ),
            
            // Mudanças
            ServiceType(
                categoryId = "mudancas",
                name = "Mudança Residencial",
                description = "Mudança completa com embalagem e transporte",
                icon = "ic_moving",
                isComplex = true,
                estimatedPrice = 400.0,
                estimatedTime = "1 dia",
                order = 1
            ),
            
            // Tecnologia
            ServiceType(
                categoryId = "tecnologia",
                name = "Suporte de TI",
                description = "Suporte técnico para computadores e redes",
                icon = "ic_it",
                estimatedPrice = 80.0,
                estimatedTime = "2 horas",
                order = 1
            ),
            ServiceType(
                categoryId = "tecnologia",
                name = "Instalação de Câmeras",
                description = "Instalação de sistema de câmeras de segurança",
                icon = "ic_it",
                isComplex = true,
                estimatedPrice = 300.0,
                estimatedTime = "1 dia",
                order = 2
            )
        )
    }
    
    /**
     * Cria prestadores de exemplo
     */
    private fun createSampleProviders(): List<ServiceProvider> {
        return listOf(
            ServiceProvider(
                userId = "provider_1",
                name = "João Silva",
                email = "joao.silva@email.com",
                phone = "(11) 99999-1111",
                rating = 4.8,
                totalReviews = 45,
                completedOrders = 120,
                categories = listOf("limpeza", "manutencao"),
                serviceTypes = listOf("limpeza_residencial", "reparo_moveis"),
                isVerified = true,
                location = "São Paulo, SP",
                bio = "Profissional experiente em limpeza e manutenção residencial"
            ),
            ServiceProvider(
                userId = "provider_2",
                name = "Maria Santos",
                email = "maria.santos@email.com",
                phone = "(11) 99999-2222",
                rating = 4.9,
                totalReviews = 67,
                completedOrders = 180,
                categories = listOf("eletrica", "encanamento"),
                serviceTypes = listOf("instalacao_tomadas", "desentupimento"),
                isVerified = true,
                location = "São Paulo, SP",
                bio = "Técnica especializada em serviços elétricos e hidráulicos"
            ),
            ServiceProvider(
                userId = "provider_3",
                name = "Carlos Oliveira",
                email = "carlos.oliveira@email.com",
                phone = "(11) 99999-3333",
                rating = 4.7,
                totalReviews = 32,
                completedOrders = 95,
                categories = listOf("pintura", "jardinagem"),
                serviceTypes = listOf("pintura_quarto", "manutencao_jardim"),
                isVerified = true,
                location = "São Paulo, SP",
                bio = "Artista plástico e jardineiro profissional"
            ),
            ServiceProvider(
                userId = "provider_4",
                name = "Ana Costa",
                email = "ana.costa@email.com",
                phone = "(11) 99999-4444",
                rating = 4.6,
                totalReviews = 28,
                completedOrders = 75,
                categories = listOf("mudancas", "tecnologia"),
                serviceTypes = listOf("mudanca_residencial", "suporte_ti"),
                isVerified = true,
                location = "São Paulo, SP",
                bio = "Especialista em mudanças e suporte técnico"
            )
        )
    }
    
    // ==================== CATEGORIAS DE SERVIÇOS ====================
    
    /**
     * Busca todas as categorias de serviços ativas
     */
    suspend fun getServiceCategories(): List<ServiceCategory> {
        return try {
            val snapshot = categoriesCollection
                .whereEqualTo("isActive", true)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ServiceCategory::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar categorias: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Busca uma categoria específica
     */
    suspend fun getServiceCategory(categoryId: String): ServiceCategory? {
        return try {
            val doc = categoriesCollection.document(categoryId).get().await()
            doc.toObject(ServiceCategory::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar categoria: ${e.message}")
            null
        }
    }
    
    // ==================== TIPOS DE SERVIÇOS ====================
    
    /**
     * Busca tipos de serviços por categoria
     */
    suspend fun getServiceTypesByCategory(categoryId: String): List<ServiceType> {
        return try {
            val snapshot = serviceTypesCollection
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("isActive", true)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ServiceType::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar tipos de serviço: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Busca todos os tipos de serviços
     */
    suspend fun getAllServiceTypes(): List<ServiceType> {
        return try {
            val snapshot = serviceTypesCollection
                .whereEqualTo("isActive", true)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ServiceType::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar tipos de serviço: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Busca um tipo de serviço específico
     */
    suspend fun getServiceType(serviceTypeId: String): ServiceType? {
        return try {
            val doc = serviceTypesCollection.document(serviceTypeId).get().await()
            doc.toObject(ServiceType::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar tipo de serviço: ${e.message}")
            null
        }
    }
    
    // ==================== PRESTADORES ====================
    
    /**
     * Busca prestadores verificados
     */
    suspend fun getVerifiedProviders(): List<ServiceProvider> {
        return try {
            val snapshot = providersCollection
                .whereEqualTo("isVerified", true)
                .whereEqualTo("isAvailable", true)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ServiceProvider::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar prestadores: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Busca prestadores por categoria
     */
    suspend fun getProvidersByCategory(categoryId: String): List<ServiceProvider> {
        return try {
            val snapshot = providersCollection
                .whereEqualTo("isVerified", true)
                .whereEqualTo("isAvailable", true)
                .whereArrayContains("categories", categoryId)
                .orderBy("rating", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ServiceProvider::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar prestadores por categoria: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Busca um prestador específico
     */
    suspend fun getProvider(providerId: String): ServiceProvider? {
        return try {
            val doc = providersCollection.document(providerId).get().await()
            doc.toObject(ServiceProvider::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar prestador: ${e.message}")
            null
        }
    }
    
    // ==================== FAVORITOS ====================
    
    /**
     * Busca favoritos do usuário
     */
    suspend fun getUserFavorites(userId: String): List<UserFavorite> {
        return try {
            val snapshot = favoritesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(UserFavorite::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar favoritos: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Adiciona um favorito
     */
    suspend fun addFavorite(userId: String, type: FavoriteType, itemId: String, itemName: String, itemDescription: String): Result<Unit> {
        return try {
            val favorite = UserFavorite(
                userId = userId,
                type = type,
                itemId = itemId,
                itemName = itemName,
                itemDescription = itemDescription
            )
            
            favoritesCollection.add(favorite).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao adicionar favorito: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Remove um favorito
     */
    suspend fun removeFavorite(userId: String, itemId: String): Result<Unit> {
        return try {
            val snapshot = favoritesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("itemId", itemId)
                .get()
                .await()
            
            if (!snapshot.isEmpty) {
                favoritesCollection.document(snapshot.documents.first().id).delete().await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao remover favorito: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Verifica se um item é favorito
     */
    suspend fun isFavorite(userId: String, itemId: String): Boolean {
        return try {
            val snapshot = favoritesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("itemId", itemId)
                .get()
                .await()
            
            !snapshot.isEmpty
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao verificar favorito: ${e.message}")
            false
        }
    }
    
    // ==================== AVALIAÇÕES ====================
    
    /**
     * Busca avaliações de um prestador
     */
    suspend fun getProviderReviews(providerId: String): List<ProviderReview> {
        return try {
            val snapshot = reviewsCollection
                .whereEqualTo("providerId", providerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ProviderReview::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao buscar avaliações: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Adiciona uma avaliação
     */
    suspend fun addReview(review: ProviderReview): Result<Unit> {
        return try {
            reviewsCollection.add(review).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceManager", "Erro ao adicionar avaliação: ${e.message}")
            Result.failure(e)
        }
    }
}

