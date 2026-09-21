package com.ironmind.app.domain.ai

/**
 * Thrown when on-device inference is requested but the model file has not been provisioned yet.
 * The UI can catch this (via the [com.ironmind.app.domain.model.SuggestionState.Error] state) to
 * prompt the user to download/place the model.
 */
class LlmModelNotFoundException(val expectedPath: String) :
    Exception("On-device model not found at: $expectedPath")
