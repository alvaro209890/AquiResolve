package com.example.loginapp.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.loginapp.OrdersTabFragment

/**
 * Adapter para o ViewPager2 das abas de pedidos
 */
class OrdersViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val isProviderContext: Boolean = false
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = if (isProviderContext) 3 else 4

    override fun createFragment(position: Int): Fragment {
        return if (isProviderContext) {
            // Prestador: 0 Disponíveis, 1 Aceitos, 2 Concluídos
            when (position) {
                0 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.DISTRIBUTING, isProviderContext = true)
                1 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.IN_PROGRESS, isProviderContext = true)
                2 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.COMPLETED, isProviderContext = true)
                else -> throw IllegalArgumentException("Posição inválida: $position")
            }
        } else {
            // Cliente: 0 Em Andamento, 1 Em Distribuição, 2 Concluídos, 3 Cancelados
            when (position) {
                0 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.IN_PROGRESS, isProviderContext = false)
                1 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.DISTRIBUTING, isProviderContext = false)
                2 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.COMPLETED, isProviderContext = false)
                3 -> OrdersTabFragment.newInstance(OrdersTabFragment.TabType.CANCELLED, isProviderContext = false)
                else -> throw IllegalArgumentException("Posição inválida: $position")
            }
        }
    }
}










