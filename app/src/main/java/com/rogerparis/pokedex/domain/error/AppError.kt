package com.rogerparis.pokedex.domain.error

sealed interface AppError {
    data object Network : AppError
    data object NotFound : AppError
    data class Unknown(val message: String?) : AppError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val error: AppError) : ApiResult<Nothing>
}
