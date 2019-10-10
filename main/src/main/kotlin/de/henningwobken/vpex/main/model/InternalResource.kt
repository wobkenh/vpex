package de.henningwobken.vpex.main.model

enum class InternalResource(val filename: String) {
    LOGO("vpex_logo.png"), //
    ICON("vpex_icon.png"), //
    VERSION("version.txt"), //
    DONATE_QR("donate_qr.png"), //
    DONATE_IMG_WHITE("donate_img_white.png"), //
    DONATE_BUTTON("donate_button.gif"), //
    EDITOR_CSS("editor.css"), //
    BANNER("banner.txt"), //

    // ICONS
    LOCK_CLOSED_ICON("material-icons/lock_closed-24px.svg"), //
    LOCK_OPEN_ICON("material-icons/lock_open-24px.svg"), //
    ERROR_ICON("material-icons/error-24px.svg"), //
    SUCCESS_ICON("material-icons/check-24px.svg"), //
    FATAL_ICON("nuclear.png"), //
    WARN_ICON("material-icons/warning-24px.svg"), //
    SEND_ICON("material-icons/send-24px.svg"), //
    FILE_ICON("material-icons/insert_drive_file-24px.svg"), //
    OPEN_IN_NEW_ICON("material-icons/open_in_new-24px.svg")
}
