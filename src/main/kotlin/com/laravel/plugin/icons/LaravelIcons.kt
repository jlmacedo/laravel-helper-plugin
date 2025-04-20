package com.laravel.plugin.icons

import com.intellij.openapi.util.IconLoader

object LaravelIcons {
    @JvmField
    val ROUTE = IconLoader.getIcon("/icons/route.svg", LaravelIcons::class.java)

    @JvmField
    val TRANSLATION = IconLoader.getIcon("/icons/translation.svg", LaravelIcons::class.java)

    @JvmField
    val VIEW = IconLoader.getIcon("/icons/view.svg", LaravelIcons::class.java)

    @JvmField
    val ASSET = IconLoader.getIcon("/icons/asset.svg", LaravelIcons::class.java)
}