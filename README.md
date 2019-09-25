# <img src="https://simplex24.de/vpex/vpex_logo.png" height="125"/>
VPEX is a very basic and lightweight editor for large text/xml files. It allows you to **V**iew, **P**arse (syntax and schema validation) and **E**dit large **X**ml files. 

VPEX was designed to be quick and reliable while working with files of around 50-200 MB. Even larger files _may_ work, but are not tested.

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
- Pagination: For large files, only a part (page) is displayed at a time. Editing functions still use the whole file content. You can configure if and when pagination is enabled.
- Auto-Updater
- All the things you like from other editors and more (undo, dirty/unchanged indicator, line count, char count, line numbering, memory usage indicator, ...)  

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
- Open and edit very large files without loading the whole file content into memory
- Syntax highlighting (with configurable threshold)
- Selection text length
- Cursor column
- XPath support
- Lock icon button to disable editing to make sure you don't accidentally change the file
- Proper example setup and unittests