package com.vcode.studio.core.navigation.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.vcode.studio.navigation.api.Navigator

class NavigatorImpl(startRoute: NavKey) : Navigator {

    override val backStack = NavBackStack(startRoute)

    override fun replaceWith(route: NavKey) {
        backStack.clear()
        backStack.add(route)
    }

    override fun navigate(route: NavKey) {
        backStack.add(route)
    }

    override fun goBack() {
        backStack.removeLastOrNull()
    }
}