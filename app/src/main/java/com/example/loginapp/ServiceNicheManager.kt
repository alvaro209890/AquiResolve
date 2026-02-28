package com.example.loginapp

import kotlinx.coroutines.delay
import java.util.*

/**
 * Gerenciador de Nichos de Serviços
 * 
 * Gerencia todos os nichos de serviços disponíveis no app
 * conforme especificado no prompt original
 */
object ServiceNicheManager {
    
    /**
     * Nicho de serviço
     */
    data class ServiceNiche(
        val id: String,
        val name: String,
        val displayName: String,
        val description: String,
        val iconRes: Int,
        val colorRes: Int,
        val isActive: Boolean = true,
        val supportsSimpleServices: Boolean = true,
        val supportsComplexServices: Boolean = true,
        val basePrice: Double = 0.0,
        val emergencyFee: Double = 0.3, // 30% para serviços de emergência
        val distanceFee: Double = 0.1 // 10% por km adicional
    )
    
    /**
     * Todos os nichos de serviços disponíveis
     */
    val allServiceNiches = listOf(
        // 1. Elétrica
        ServiceNiche(
            id = "electrical",
            name = "Elétrica",
            displayName = "Elétrica",
            description = "Instalações elétricas, reparos, troca de chuveiro, instalação de tomadas",
            iconRes = R.drawable.ic_electrician,
            colorRes = R.color.electrical_color,
            basePrice = 80.0
        ),
        
        // 2. Encanador
        ServiceNiche(
            id = "plumbing",
            name = "Encanador", 
            displayName = "Encanador",
            description = "Desentupimentos, troca de torneiras, reparos hidráulicos",
            iconRes = R.drawable.ic_plumber,
            colorRes = R.color.plumbing_color,
            basePrice = 70.0
        ),
        
        // 3. Pintura
        ServiceNiche(
            id = "painting",
            name = "Pintura",
            displayName = "Pintura e Acabamento",
            description = "Pintura residencial e comercial, acabamentos",
            iconRes = R.drawable.ic_painter,
            colorRes = R.color.painting_color,
            basePrice = 120.0
        ),
        
        // 4. Marcenaria
        ServiceNiche(
            id = "carpentry",
            name = "Marcenaria",
            displayName = "Marcenaria e Montagem",
            description = "Montagem de móveis, reparos em madeira, móveis planejados",
            iconRes = R.drawable.ic_carpentry,
            colorRes = R.color.carpentry_color,
            basePrice = 150.0
        ),
        
        // 5. Limpeza
        ServiceNiche(
            id = "cleaning",
            name = "Limpeza",
            displayName = "Limpeza Residencial e Comercial",
            description = "Limpeza de casas, escritórios, pós-obra",
            iconRes = R.drawable.ic_cleaning,
            colorRes = R.color.cleaning_color,
            basePrice = 60.0
        ),
        
        // 6. Jardinagem
        ServiceNiche(
            id = "gardening",
            name = "Jardinagem",
            displayName = "Jardinagem e Paisagismo",
            description = "Manutenção de jardins, paisagismo, poda de árvores",
            iconRes = R.drawable.ic_gardening,
            colorRes = R.color.gardening_color,
            basePrice = 90.0
        ),
        
        // 7. Dedetização
        ServiceNiche(
            id = "pest_control",
            name = "Dedetização",
            displayName = "Dedetização e Controle de Pragas",
            description = "Controle de insetos, roedores, pragas urbanas",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.pest_control_color,
            basePrice = 200.0
        ),
        
        // 8. Climatização
        ServiceNiche(
            id = "hvac",
            name = "Climatização",
            displayName = "Instalação e Manutenção de Climatização",
            description = "Ar condicionado, ventilação, aquecimento",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.hvac_color,
            basePrice = 180.0
        ),
        
        // 9. Eletrodomésticos
        ServiceNiche(
            id = "appliances",
            name = "Eletrodomésticos",
            displayName = "Instalação de Eletrodomésticos",
            description = "Instalação de geladeiras, máquinas de lavar, fogões",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.appliances_color,
            basePrice = 100.0
        ),
        
        // 10. Informática
        ServiceNiche(
            id = "it",
            name = "Informática",
            displayName = "Informática e Redes",
            description = "Configuração de equipamentos, redes Wi-Fi, suporte técnico",
            iconRes = R.drawable.ic_it,
            colorRes = R.color.it_color,
            basePrice = 80.0
        ),
        
        // 11. Serralheria
        ServiceNiche(
            id = "locksmith",
            name = "Serralheria",
            displayName = "Serralheria",
            description = "Abertura de portas, troca de fechaduras, chaves",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.locksmith_color,
            basePrice = 120.0
        ),
        
        // 12. Automação
        ServiceNiche(
            id = "automation",
            name = "Automação",
            displayName = "Automação Residencial",
            description = "Câmeras de segurança, alarmes, automação inteligente",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.automation_color,
            basePrice = 250.0
        ),
        
        // 13. Mudanças
        ServiceNiche(
            id = "moving",
            name = "Mudanças",
            displayName = "Mudanças e Fretes",
            description = "Serviços de mudança, transporte de móveis, fretes",
            iconRes = R.drawable.ic_moving,
            colorRes = R.color.moving_color,
            basePrice = 300.0
        ),
        
        // 14. Pequenos Reparos
        ServiceNiche(
            id = "repairs",
            name = "Pequenos Reparos",
            displayName = "Pequenos Reparos Domésticos",
            description = "Reparos gerais, manutenção preventiva, ajustes",
            iconRes = R.drawable.ic_services,
            colorRes = R.color.repairs_color,
            basePrice = 50.0
        )
    )
    
    /**
     * Nichos que suportam serviços simples (preço fixo)
     */
    val simpleServiceNiches = allServiceNiches.filter { it.supportsSimpleServices }
    
    /**
     * Nichos que suportam serviços complexos (orçamento)
     */
    val complexServiceNiches = allServiceNiches.filter { it.supportsComplexServices }
    
    /**
     * Nichos ativos
     */
    val activeServiceNiches = allServiceNiches.filter { it.isActive }
    
    /**
     * Obtém nicho por ID
     */
    fun getNicheById(id: String): ServiceNiche? {
        return allServiceNiches.find { it.id == id }
    }
    
    /**
     * Obtém nicho por nome
     */
    fun getNicheByName(name: String): ServiceNiche? {
        return allServiceNiches.find { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * Calcula preço base para um serviço
     */
    fun calculateBasePrice(nicheId: String): Double {
        val niche = getNicheById(nicheId) ?: return 0.0
        
        return niche.basePrice
    }
    
    /**
     * Calcula taxa de deslocamento
     */
    fun calculateDistanceFee(nicheId: String, distanceKm: Double): Double {
        val niche = getNicheById(nicheId) ?: return 0.0
        
        if (distanceKm <= 5.0) return 0.0 // Sem taxa para até 5km
        
        val additionalKm = distanceKm - 5.0
        return niche.basePrice * niche.distanceFee * additionalKm
    }
    
    /**
     * Obtém nichos por categoria
     */
    fun getNichesByCategory(category: String): List<ServiceNiche> {
        return when (category.lowercase()) {
            "simple" -> simpleServiceNiches
            "complex" -> complexServiceNiches
            "all" -> allServiceNiches
            "active" -> activeServiceNiches
            else -> allServiceNiches
        }
    }
    
    /**
     * Busca nichos por termo
     */
    fun searchNiches(query: String): List<ServiceNiche> {
        if (query.isEmpty()) return allServiceNiches
        
        return allServiceNiches.filter { niche ->
            niche.name.contains(query, ignoreCase = true) ||
            niche.displayName.contains(query, ignoreCase = true) ||
            niche.description.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * Obtém estatísticas dos nichos
     */
    suspend fun getNicheStats(): NicheStats {
        delay(300) // Simular delay
        
        return NicheStats(
            totalNiches = allServiceNiches.size,
            activeNiches = activeServiceNiches.size,
            simpleServiceNiches = simpleServiceNiches.size,
            complexServiceNiches = complexServiceNiches.size,
            averageBasePrice = allServiceNiches.map { it.basePrice }.average()
        )
    }
    
    /**
     * Estatísticas dos nichos
     */
    data class NicheStats(
        val totalNiches: Int,
        val activeNiches: Int,
        val simpleServiceNiches: Int,
        val complexServiceNiches: Int,
        val averageBasePrice: Double
    )
} 