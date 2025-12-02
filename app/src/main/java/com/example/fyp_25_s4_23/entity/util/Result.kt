package com.example.fyp_25_s4_23.entity.util

sealed class Result<out T> {
    data class Ok<T>(val value: T) : Result<T>()
    data class Err(val throwable: Throwable) : Result<Nothing>()
}

