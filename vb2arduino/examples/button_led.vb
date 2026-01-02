' Button on pin 0, LED on pin 2
Const LED = 2
Const BUTTON = 0

Sub Setup()
    PinMode LED, OUTPUT
    PinMode BUTTON, INPUT_PULLUP
End Sub

Sub Loop()
    If DigitalRead(BUTTON) = LOW Then
        DigitalWrite LED, HIGH
    Else
        DigitalWrite LED, LOW
    End If
End Sub
