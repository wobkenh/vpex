# ![Vpex](/updater/src/main/resources/vpex_logo.png?raw=true)

VPEX is a very basic and lightweight editor for large text/xml files. It allows you to **V**iew, **P**arse (syntax and schema validation) and **E**dit large **X**ml files. 

VPEX was designed to be quick and reliable while working with files of any size.

## Installation

VPEX runs on the JVM. You can download the jar from [https://simplex24.de/vpex/vpex.jar](https://simplex24.de/vpex/vpex.jar). 
You can place the Jar wherever you want. As long as Java is installed and configured as default for .jar files, you can start vpex simply by double clicking the jar. 

The config, logs and updater jar (if autoupdate is configured) will be saved in the `.vpex` directory in your home/user directory.

## Motivation

Common Texteditors like Notepad++ often have problems viewing and validating large xml files. Even unix programs like `less` sometimes struggle with large files.
Since I sometimes work with files which are up to 200MB in size, I was in need for a tool that would let me open and validate large xml files.
And so, the idea for vpex was born.

## Features

- Syntax validation
- Schema validation with automatic schema detection (specify a schema directory via config)
- Pretty print
- "Ugly print" = Condense into a single line (Exception: Data and comments are preserved as is)
- Actions like validation and formatting are done asynchronously as to not block the ui
- Display Modes: To always offer the best performance, VPEX chooses one of three display modes depending on the size of the file. The thresholds for the display modes are fully configurable. The three display modes are:
    - plain: Read the file into memory and display everything
    - pagination: Read the file into memory and display only one page at a time
    - disk pagination: Only read one page at a time into memory
- Auto-Updater (cacerts and proxy options included)
- All the things you like from other editors and more (undo, find/replace, dirty/unchanged indicator, line count, char count, line numbering, memory usage indicator, ...)  

## Technology Stack
VPEX is a Desktop Application written in Kotlin using the TornadoFX Framework, which itself makes use of the JavaFX toolkit.
The Textarea used is from the library RichTextFX.

VPEX is a maven multi-module projects. The modules are:
- main (Main Program)
- updater (A small programm used to restart the main program after an update)

## Contributing

VPEX is currently work in progress.
Feel free to fix bugs or add new features, as long as the performance does not suffer from your changes so that vpex stays reasonable fast.

If you cloned the repository and want to build a jar, use the standard `mvn clean install` goals on the parent project. 
To start the program while developing, I usually use a TornadoFX IntelliJ Run Configuration provided by the TornadoFX IntelliJ PlugIn.

If you do not know what to work on, you can chose a task from the todo list below.

## TODOs

- List-all-feature to quickly overview all matches of a search query
- Native packaging (e.g. using java packager)
- Encoding detection and conversion
- Find/replace history
- Opened files history
- Ignore syntax on pretty print
- Comment out shortcut
- Syntax highlighting (with configurable threshold)
- Selection text length
- Cursor column
- XPath support
- Allow editing in disk pagination display mode
- Allow opening of multiple files using tabs
- Increase unit test coverage
