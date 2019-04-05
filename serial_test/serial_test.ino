//FAKE TEENSY sending FAKE DATA

/*   Reads SD card and sends data over serial
 *   
 *   IH 2/9/19
 *   
 */

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

#define BLINK_DELAY 2000 // milliseconds

const int SD_CS = 10; // pin assignment
const int led = 13; 
// https://forum.pjrc.com/threads/26412-How-to-remap-pins-so-that-the-T3-built-in-LED-isn-t-OR-d-with-the-SPI-SCK

unsigned long lastOnTime;

void setup() {
    Serial.begin(9600); // baud rate is ignored for Teensy
    // https://www.pjrc.com/teensy/td_serial.html
    // it just sends everything at full USB rate - ok bc this is NOT
    // a direct serial connection w/ another device, it's through USB
}


void loop() {
    if (Serial.available()) {
        int byteRead = Serial.read();

        switch (byteRead) {
            case inquiry: 
            {
                sendDirectory();
                Serial.write(end_transmission);

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


void sendFile(String filename, String contents) {
    Serial.write(start_filename);
    Serial.print(filename);
    Serial.write(start_file);
    Serial.print(contents);
    Serial.write(end_file);
    Serial.println();
}


void sendDirectory() {  // copied largely from the arduino example file
    sendFile("190111_I.TXT","TONS OF TEXT FAKE DATA BLAH BLAH BLAH");
    sendFile("190117_A.TXT","MORE FAKE DATA BLAH BLAH BLAHDI BLOO");
}


