<?php

Route::get('/', function () {
    return view('home');
})->name('home');

Route::resource('users', 'UserController');

Route::get('/dashboard', function () {
    return view('dashboard');
})->name('dashboard');

Route::get('/profile', 'ProfileController@show')->name('profile.show');
Route::put('/profile', 'ProfileController@update')->name('profile.update');

Route::prefix('admin')->group(function () {
    Route::get('/', 'AdminController@index')->name('admin.index');
    Route::get('/users', 'AdminController@users')->name('admin.users');
    Route::get('/settings', 'AdminController@settings')->name('admin.settings');
});

Route::post('/users', 'UserController@store')->name('users.store');
Route::get('/users/{user}', 'UserController@show')->name('users.show');
