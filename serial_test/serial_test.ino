//single character read back

int incomingByte = 0;  // for incoming serial data

void setup() {
  Serial.begin(9600); // opens serial port, sets data rate to 9600 bps
  String input = "";
}

void loop() {

  // reply only when you receive data:
  if (Serial.available() > 0) {
    // read the incoming byte:
    incomingByte = Serial.read();
    char incomingChar = incomingByte;

    //if character is $, designated trigger, send all data
    if (incomingByte == 36) {
      Serial.print("file1<START>Here is a bunch of bullshit data, end character<BREAK>file2<START>bullshit data end character<BREAK>file3<START>thats all");

    //otherwise say what you got
    } else {
      Serial.print("I received: ");
      Serial.println(incomingChar);
      Serial.println(incomingByte);
    }
  }
}

