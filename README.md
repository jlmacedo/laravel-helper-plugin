# Laravel Plugin for JetBrains IDEs :heart:

[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/23910-laravel-plugin.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/23910-laravel-plugin)
[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/d/23910-laravel-plugin.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/23910-laravel-plugin)
[![Likes](https://img.shields.io/endpoint?url=https://plugins.jetbrains.com/api/plugins/23910/likes)](https://plugins.jetbrains.com/plugin/23910-laravel-plugin/reviews)

Enhance your Laravel development experience within JetBrains IDEs like PHPStorm with this powerful plugin! This plugin provides intelligent code insight, navigation, and more, specifically tailored for Laravel projects.

## :sparkles: Features

This plugin currently provides the following awesome features:

*   **Route Navigation:**
    *   Navigate directly to route definitions from `route()` calls, route names in Blade templates, and JavaScript files.
    *   Supports named routes, route paths, and route actions.
    *   Quickly find where routes are defined and used.
    *   Support for route parameters definition.
    *   Support for route middleware definition and navigation.
    *   Support for route Domain definition and navigation.
    *   Support for route prefix definition and navigation.
    *   Support for route controller definition and navigation.
    *   Support for route where clause definition and navigation.
    *   Support for route definitions inside group.
    *   Support for route definitions with closures.
*   **Route Completion:**
    *   Intelligent code completion for route names and paths within `route()` functions, blade templates and JS files.
    *   Displays route HTTP methods (GET, POST, etc.) alongside completion suggestions.
    *   Shows the route path in the completion suggestion, so you can see the complete context.
*   **View Navigation:**
    *   Navigate to view files directly from `view()` calls and Blade directives like `@include`, `@extends`, `@component` and `@livewire`.
*   **Asset Navigation:**
    *   Navigate to asset files from `asset()`, `mix()`, and `vite()` helper functions, as well as Blade directives like `@asset`, `@mix`, and `@vite`.
    *   Supports HTML attributes (`src`, `href`, `data-src`, etc.) that contain asset paths.
*   **Laravel Context Awareness:**
    *   The plugin intelligently recognizes Laravel-specific functions and constructs, ensuring accurate functionality.
* **Route details:**
    * When you navigate to a route definition, you can see the full details of the route like:
        * name
        * method
        * uri
        * action
        * middleware
        * parameters
        * domain
        * prefix
        * where constraints
* **Route Usage:**
    * See all usages of a specific route.
* **Domain Usages:**
    * See all usages of a specific Domain in a route.

## :construction: Under Development

*   **Translation Completion and Navigation:** This feature is currently under development and will be available in a future release. (Soon:tm:)

## :rocket: Installation

1.  Open your JetBrains IDE (e.g., PHPStorm).
2.  Go to `File` -> `Settings` (or `Preferences` on macOS).
3.  Select `Plugins`.
4.  Search for "Laravel Plugin".
5.  Click "Install".
6.  Restart your IDE.

## :handshake: Contributing

We welcome contributions! If you have any ideas for new features, bug fixes, or improvements, please open an issue or submit a pull request on our GitHub repository: [https://github.com/jlmacedo/laravel-helper-plugin](https://github.com/your-github-repo)

## :pray: Support the Project

This plugin is an open-source project developed in our free time. If you find it helpful and want to support its continued development, you can donate via PayPal:

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=GWRQXTAB2D7T4)

**PayPal Email:** jlmacedo89@hotmail.com

Any amount is greatly appreciated! Your support helps us dedicate more time to improving the plugin and adding new features.

## :question: Known Issues

*   Translation completion and navigation are not currently implemented. This is actively being worked on.

## :copyright: License

This project is licensed under the [MIT License](LICENSE).

---

:rocket: Happy Laravel Coding! :rocket: