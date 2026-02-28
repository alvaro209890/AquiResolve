package com.example.loginapp.models

/**
 * Tabela de preços dos serviços
 * Pair(precoCliente, valorPrestador)
 */
object ServicePricing {

    private fun normalizeCategory(category: String): String {
        return when {
            category.equals("Hidráulica", ignoreCase = true) -> "Encanador"
            category.equals("Encanador", ignoreCase = true) -> "Encanador"
            category.equals("Estofados", ignoreCase = true) -> "Limpeza de estofados"
            category.equals("Limpeza de estofados", ignoreCase = true) -> "Limpeza de estofados"
            else -> category
        }
    }

    private val pricingTable = mapOf(
        "Elétrica" to mapOf(
            "Instalação de lâmpadas" to Pair(0.10, 0.05),
            "Instalação de tomada" to Pair(110.0, 55.0),
            "Troca de disjuntor" to Pair(150.0, 75.0),
            "Instalação de interruptor" to Pair(110.0, 55.0),
            "Instalação de chuveiro" to Pair(150.0, 75.0),
            "Instalação de resistência" to Pair(110.0, 55.0),
            "Instalação de luminária" to Pair(150.0, 75.0),
            "Instalação de spots" to Pair(110.0, 55.0),
            "Revisão Elétrica (até 7 pontos)" to Pair(200.0, 100.0)
        ),

        "Encanador" to mapOf(
            "Troca de torneira" to Pair(160.0, 80.0),
            "Troca de rabicho" to Pair(160.0, 80.0),
            "Troca de sifão" to Pair(110.0, 55.0),
            "Troca de Filtro" to Pair(160.0, 80.0),
            "Troca de reparos de registro" to Pair(160.0, 80.0),
            "Troca de reparos de torneira" to Pair(160.0, 80.0),
            "Troca kit de caixa acoplada" to Pair(160.0, 80.0),
            "Reparos de descarga de parede" to Pair(160.0, 80.0),
            "Revisão hidráulica (até 7 pontos)" to Pair(160.0, 80.0),
            "Vazamentos" to Pair(120.0, 60.0),
            "Troca de torneira monobloco" to Pair(260.0, 130.0)
        ),

        "Instalação" to mapOf(
            "Instalação de Suporte de tv" to Pair(160.0, 80.0),
            "Instalação de ventilador de teto" to Pair(190.0, 95.0),
            "Instalação de máquina de lavar" to Pair(190.0, 95.0),
            "Instalação de Lava louça" to Pair(190.0, 95.0),
            "Instalação de Fogão Cooktop" to Pair(180.0, 90.0),
            "Instalação de Purificador" to Pair(160.0, 80.0),
            "Conversão de gás para fogão cooktop" to Pair(130.0, 65.0),
            "Varal de teto" to Pair(150.0, 75.0)
        ),

        "Caixa d'água" to mapOf(
            "Limpeza de caixa d'água de 1000 litros" to Pair(150.0, 75.0),
            "Limpeza de caixa d'água de 2000 litros" to Pair(250.0, 125.0),
            "Limpeza de caixa d'água de 3000 litros" to Pair(350.0, 175.0),
            "Limpeza de caixa d'água de 4000 litros" to Pair(450.0, 225.0),
            "Limpeza de caixa d'água de 5000 litros" to Pair(550.0, 275.0),
            "Troca de boia" to Pair(150.0, 75.0)
        ),

        "Desentupimento manual" to mapOf(
            "Desentupimento de pia" to Pair(180.0, 90.0),
            "Desentupimento ralo" to Pair(180.0, 90.0),
            "Desentupimento vaso" to Pair(180.0, 90.0)
        ),

        "Desentupimento com maquinário" to mapOf(
            "Até 2 metros" to Pair(200.0, 100.0),
            "Adicional por Metro" to Pair(90.0, 45.0)
        ),

        "Caça-vazamentos" to mapOf(
            "Caça-vazamentos" to Pair(550.0, 385.0)
        ),

        "Limpeza de estofados" to mapOf(
            "Limpeza de sofá 2 lugares" to Pair(215.0, 129.0),
            "Limpeza de sofá 3 lugares" to Pair(265.0, 159.0),
            "Limpeza de sofá retrátil" to Pair(265.0, 159.0),
            "Limpeza de sofá de canto" to Pair(265.0, 159.0),
            "Limpeza de poltronas estofadas" to Pair(195.0, 117.0),
            "Limpeza de tapetes pequenos (até 2 mts)" to Pair(215.0, 129.0),
            "Limpeza de cadeiras estofadas" to Pair(195.0, 117.0),
            "Limpeza de carpetes pequenos (até 2mts)" to Pair(215.0, 129.0),
            "Higienização de colchões Casal" to Pair(215.0, 129.0),
            "Colchão solteiro" to Pair(145.0, 87.0),
            "Colchão king" to Pair(315.0, 189.0),
            "Colchão queen" to Pair(265.0, 159.0),
            "Impermeabilização" to Pair(65.0, 39.0)
        ),

        "Eletrodomésticos" to mapOf(
            "Conserto de micro-ondas" to Pair(160.0, 80.0),
            "Reparo de fogão e forno" to Pair(160.0, 80.0),
            "Reparo de pequenos eletrodomésticos" to Pair(160.0, 80.0),
            "Instalação de eletrodomésticos" to Pair(190.0, 95.0),
            "Geladeira e freezer" to Pair(250.0, 125.0),
            "Máquina de lavar" to Pair(180.0, 90.0)
        ),

        "Chaveiro residencial" to mapOf(
            "Abertura de portas residencial" to Pair(180.0, 108.0),
            "Ajuste de fechaduras" to Pair(180.0, 108.0),
            "Instalação de fechadura eletrônica e digital" to Pair(280.0, 168.0),
            "Extração de chave" to Pair(150.0, 90.0)
        ),

        "Serviços automotivos" to mapOf(
            "Abertura de portas de veículos" to Pair(180.0, 90.0),
            "Extração de chaves quebradas" to Pair(180.0, 90.0),
            "Remendo de pneu" to Pair(80.0, 40.0),
            "Remendo de pneu Caminhonete, SUV e vans" to Pair(115.0, 57.50),
            "Troca de pneu no local" to Pair(85.0, 42.50),
            "Troca de pneu Caminhonete, SUV e vans" to Pair(115.0, 57.50),
            "Pane seca (entrega de combustível)" to Pair(85.0, 42.50),
            "Partida elétrica" to Pair(120.0, 60.0)
        ),

        "Montagem de móveis" to mapOf(
            "Guarda roupas" to Pair(0.0, 100.0),
            "Cama" to Pair(0.0, 90.0),
            "Mesa" to Pair(0.0, 75.0),
            "Cômoda" to Pair(0.0, 75.0),
            "Armário" to Pair(0.0, 75.0),
            "Escrivaninha" to Pair(0.0, 75.0),
            "Prateleiras" to Pair(0.0, 65.0),
            "Objetos de cozinha" to Pair(0.0, 65.0),
            "Objetos de banheiro" to Pair(0.0, 65.0)
        ),

        "Faxina" to mapOf(
            "Faxina Básica (apt pequeno 1 a 2 quartos) - 4h a 5h" to Pair(190.0, 133.0),
            "Faxina completa (apt/casa média 2 a 3 quartos) - 6h a 8h" to Pair(250.0, 175.0),
            "Faxina pesada (casa grande, pós-obra, mudança) - 10h" to Pair(450.0, 315.0)
        ),

        "Ar condicionado" to mapOf(
            "9 a 12 mil BTUs split" to Pair(650.0, 364.0),
            "18 a 30 mil BTUs" to Pair(750.0, 420.0),
            "Ar de janela" to Pair(220.0, 123.20),
            "Higienização de 9 a 30 mil BTUs" to Pair(300.0, 168.0)
        )
    )

    /**
     * Obter preço do cliente para um serviço específico
     */
    fun getPrice(category: String, serviceType: String): Double? {
        val categoryPrices = pricingTable[normalizeCategory(category)]
        if (categoryPrices != null) {
            val pair = categoryPrices[serviceType]
            if (pair != null && pair.first > 0) return pair.first

            categoryPrices.forEach { (key, value) ->
                if (key.equals(serviceType, ignoreCase = true) && value.first > 0) {
                    return value.first
                }
            }
        }

        pricingTable.forEach { (_, services) ->
            services.forEach { (key, value) ->
                if (key.equals(serviceType, ignoreCase = true) && value.first > 0) {
                    return value.first
                }
            }
        }

        return null
    }

    /**
     * Obter valor que o prestador recebe por um serviço específico
     */
    fun getProviderValue(category: String, serviceType: String): Double? {
        val categoryPrices = pricingTable[normalizeCategory(category)]
        if (categoryPrices != null) {
            val pair = categoryPrices[serviceType]
            if (pair != null && pair.second > 0) return pair.second

            categoryPrices.forEach { (key, value) ->
                if (key.equals(serviceType, ignoreCase = true) && value.second > 0) {
                    return value.second
                }
            }
        }

        pricingTable.forEach { (_, services) ->
            services.forEach { (key, value) ->
                if (key.equals(serviceType, ignoreCase = true) && value.second > 0) {
                    return value.second
                }
            }
        }

        return null
    }

    /**
     * Obter preço padrão por categoria (preço do cliente)
     */
    fun getDefaultPrice(category: String): Double {
        return when {
            category.contains("Elétrica", ignoreCase = true) -> 110.0
            category.contains("Encanador", ignoreCase = true) -> 160.0
            category.contains("Hidráulica", ignoreCase = true) -> 160.0
            category.contains("Instalação", ignoreCase = true) -> 160.0
            category.contains("Faxina", ignoreCase = true) -> 190.0
            category.contains("Desentupimento", ignoreCase = true) -> 180.0
            category.contains("Limpeza de estofados", ignoreCase = true) -> 215.0
            category.contains("Estofado", ignoreCase = true) -> 215.0
            category.contains("Chaveiro", ignoreCase = true) -> 180.0
            category.contains("Automotivo", ignoreCase = true) -> 115.0
            category.contains("Montagem", ignoreCase = true) -> 0.0
            category.contains("Ar condicionado", ignoreCase = true) -> 650.0
            category.contains("Caça-vazamento", ignoreCase = true) -> 550.0
            category.contains("Caixa", ignoreCase = true) -> 150.0
            category.contains("Eletrodoméstico", ignoreCase = true) -> 160.0
            else -> 100.0
        }
    }

    /**
     * Obter valor padrão do prestador por categoria
     */
    fun getDefaultProviderValue(category: String): Double {
        return when {
            category.contains("Elétrica", ignoreCase = true) -> 55.0
            category.contains("Encanador", ignoreCase = true) -> 80.0
            category.contains("Hidráulica", ignoreCase = true) -> 80.0
            category.contains("Instalação", ignoreCase = true) -> 80.0
            category.contains("Faxina", ignoreCase = true) -> 133.0
            category.contains("Desentupimento", ignoreCase = true) -> 90.0
            category.contains("Limpeza de estofados", ignoreCase = true) -> 129.0
            category.contains("Estofado", ignoreCase = true) -> 129.0
            category.contains("Chaveiro", ignoreCase = true) -> 108.0
            category.contains("Automotivo", ignoreCase = true) -> 57.50
            category.contains("Montagem", ignoreCase = true) -> 75.0
            category.contains("Ar condicionado", ignoreCase = true) -> 364.0
            category.contains("Caça-vazamento", ignoreCase = true) -> 385.0
            category.contains("Caixa", ignoreCase = true) -> 75.0
            category.contains("Eletrodoméstico", ignoreCase = true) -> 80.0
            else -> 50.0
        }
    }

    /**
     * Verificar se um serviço tem valor "a consultar"
     */
    fun isConsultPrice(category: String, serviceType: String): Boolean {
        val pair = pricingTable[normalizeCategory(category)]?.get(serviceType)
        return pair != null && pair.first == 0.0 && pair.second == 0.0
    }

    /**
     * Obter lista de serviços por categoria
     */
    fun getServicesForCategory(category: String): List<String> {
        return pricingTable[normalizeCategory(category)]?.keys?.toList() ?: emptyList()
    }

    /**
     * Obter todas as categorias disponíveis
     */
    fun getAllCategories(): List<String> {
        return pricingTable.keys.toList()
    }

    /**
     * Obter tabela de valores do prestador (categoria -> lista de Pair(servico, valor))
     * Usado para exibir na tela de configurações do prestador
     */
    fun getProviderPricingTable(): Map<String, List<Pair<String, Double>>> {
        return pricingTable.mapValues { (_, services) ->
            services.map { (serviceName, prices) ->
                Pair(serviceName, prices.second)
            }
        }
    }
}
