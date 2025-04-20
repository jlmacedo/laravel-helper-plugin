package com.laravel.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base test class for Laravel plugin tests
 * This class extends BasePlatformTestCase to provide testing utilities
 */
open class LaravelTestCase : BasePlatformTestCase() {
    // BasePlatformTestCase already inherits the assertion methods from TestCase,
    // so we don't need to redefine them here.
}
