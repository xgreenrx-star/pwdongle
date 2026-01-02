' Serial echo at 115200
Const BAUD = 115200
Dim incoming As Integer

Sub Setup()
    SerialBegin BAUD
End Sub

Sub Loop()
    If SerialAvailable() > 0 Then
        incoming = SerialRead()
        SerialPrintLine incoming
    End If
End Sub
