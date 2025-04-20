<html>
<head>
    <title>Home</title>
    <link rel="stylesheet" href="{{ asset('css/app.css') }}">
</head>
<body>
    <div class="container">
        <header>
            <h1>Welcome to Laravel</h1>
        </header>
        <main>
            <p>{{ __('messages.welcome') }}</p>
            <p>This is a test view for the Laravel Plugin.</p>
        </main>
        <footer>
            <p>&copy; {{ date('Y') }} Laravel</p>
        </footer>
    </div>
    <script src="{{ mix('js/app.js') }}"></script>
</body>
</html>
