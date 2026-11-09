package com.examprep.ai.client;

/**
 * @param systemInstruction trusted application instructions only
 * @param userPrompt        task instructions plus delimited, untrusted study material
 * @param maxOutputTokens   upper bound for the response
 */
public record GeminiPrompt(String systemInstruction, String userPrompt, int maxOutputTokens) {
}
