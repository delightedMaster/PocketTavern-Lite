package me.rerere.rikkahub.service

sealed class ChatException(override val message: String) : RuntimeException(message)
class BadRequestException(message: String) : ChatException(message)
class NotFoundException(message: String) : ChatException(message)
