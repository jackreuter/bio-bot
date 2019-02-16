//FAKE TEENSY sending FAKE DATA

// unique ID for unit
#define ID "001AA"

// define characters marking start of filename, start of file, end of file, end of transmission
// using ascii control characters - hope they don't interfere w/ serial transfer
#define start_filename '!' // control char would be 0x01
#define start_file '$' // 0x02
#define end_file '^' // 0x03
#define end_transmission '&' // 0x04
#define inquiry '~' // 0x05
#define identity '*' // request for identity

int incomingByte = 0;  // for incoming serial data

void setup() {
  Serial.begin(9600); // opens serial port, sets data rate to 9600 bps
  String input = "";
}

void loop() {

  // reply only when you receive data:
  if (Serial.available() > 0) {
        if (Serial.available()) {
        int byteRead = Serial.read();

        switch (byteRead) {
            case inquiry: 
            {
                //File folder = SD.open("/data/"); // apparently a directory is a special kind of file
                //sendDirectory(folder);
                Serial.write(start_filename);
                Serial.write("190111_I.TXT");
                Serial.write(start_file);
                Serial.write("TONS OF TEXT FAKE DATA BLAH BLAH BLAH");
                Serial.write(end_file);
                Serial.write(start_filename);
                Serial.write("190117_A.TXT");
                Serial.write(start_file);
                Serial.write("MORE FAKE TEXT IN A NEW FILE OF TEXT YAA");
                Serial.write(end_file);
                Serial.write(end_transmission);
                //folder.close();

                break;
            }

            case identity:
            {
                Serial.println(ID);
                break;
            }

            case 'q': 
            {
                Serial.println("Yes, I am alive");
                break;
            }
        }
    }
  }
    /**
    // read the incoming byte:
    incomingByte = Serial.read();
    char incomingChar = incomingByte;

    //if character is $, designated trigger, send all data
    if (incomingByte == 36) {
      Serial.print("$file1<START>Here is a bunch of bullshit data, end character<BREAK>file2<START>bullshit data end character<BREAK>file3<START>thats all");

    //otherwise say what you got
    } else {
      Serial.print("I received: ");
      Serial.println(incomingChar);
      Serial.println(incomingByte);
    }
  }
  */
}

