# VPEX
VPEX is a very basic and lightweight editor for large text/xml files. It allows you to **V**iew, **P**arse (syntax and schema validation) and **E**dit large **X**ml files. 

VPEX was designed to be quick and reliable while working with files of around 50-200 MB. Even larger files _may_ work, but are not tested.

## Motivation

Common Texteditors like Notepad++ often have problems viewing and validating large xml files. Even unix programs like `less` sometimes struggle with large files.
Since I sometimes work with files which are up to 200MB in size, I was in need for a tool that would let me open and validate large xml files.
And so, the idea for vpex was born. It has a very basic feature set, but does exactly what I need it to do.  


## Contributing

VPEX is currently work in progress.
Feel free to fix bugs or add new features, as long as the performance does not suffer from your changes so that vpex stays reasonable fast.

If you cloned the repository and want to build a jar, use the jfx:jar maven goal (e.g. `mvn clean jfx:jar`). 
To start the program while developing, I usually use a TornadoFX IntelliJ Run Configuration provided by the TornadoFX IntelliJ PlugIn.