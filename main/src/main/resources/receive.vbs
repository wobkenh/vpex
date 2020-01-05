Set WshShell = CreateObject("WScript.Shell")

userHome = WshShell.ExpandEnvironmentStrings("%USERPROFILE%")

Set fso=CreateObject("Scripting.FileSystemObject")

' Write
outFile=userHome & "\.vpex\vpex.receive"
Set objFile = fso.CreateTextFile(outFile,True)
objFile.Write WScript.Arguments(0) & vbCrLf
objFile.Close

If NOT (fso.FileExists(userHome & "\.vpex\vpex.running")) Then
   WshShell.Run "javaw -jar " &  chr(34) & "<VPEX_PATH>" & chr(34)
End If
