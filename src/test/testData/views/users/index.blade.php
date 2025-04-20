@extends('layouts.app')

@section('content')
    <h1>Users</h1>
    <div class="users-list">
        @foreach($users as $user)
            @include('users.partials.user-card')
        @endforeach
    </div>
@endsection