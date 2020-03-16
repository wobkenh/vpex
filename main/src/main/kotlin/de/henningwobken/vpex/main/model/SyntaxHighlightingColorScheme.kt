package de.henningwobken.vpex.main.model

enum class SyntaxHighlightingColorScheme(val displayName: String, val internalResource: InternalResource) {
    DEFAULT("Default", InternalResource.SYNTAX_DEFAULT_CSS),
    NOTEPAD("Notepad++", InternalResource.SYNTAX_NOTEPAD_CSS),
    INTELLIJ("IntelliJ", InternalResource.SYNTAX_INTELLIJ_CSS),
    DRACULA("Dracula (Experimental)", InternalResource.SYNTAX_DRACULA_CSS);
}
