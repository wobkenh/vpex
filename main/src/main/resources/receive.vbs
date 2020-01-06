' <VPEX_PATH>
Set WshShell = CreateObject("WScript.Shell")

userHome = WshShell.ExpandEnvironmentStrings("%USERPROFILE%")

Set fso=CreateObject("Scripting.FileSystemObject")

' Generate random number to use in filename
dim r
randomize
r = int(rnd*1000000) + 1

' Write receive file with filepath
outFile=userHome & "\.vpex\vpex.receive" & r
Set objFile = fso.CreateTextFile(outFile,True)
objFile.Write WScript.Arguments(0) & vbCrLf
objFile.Close

' Start Vpex if not already started
If NOT (fso.FileExists(userHome & "\.vpex\vpex.running")) Then
   WshShell.Run "javaw -jar " &  chr(34) & "<VPEX_PATH>" & chr(34)
End If
