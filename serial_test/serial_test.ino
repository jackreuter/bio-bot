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
    sendFile("190117_A.TXT","2019 1 11  14  36  54  3.42  4 14.27 0 0 0 \n2019  1 11  14  37  6 3.42  4 21.55 0 0 0 7570  0.00  0.00  0.00.0  574.06  20.32 616 429 224.54  55.01 348 128 701.07  0.67  707 694 10.00\n2019  1 11  14  37  16  13.42 4 31.55 0 0 0 \nYear  Month Day Hour  Minute  Second  Time into program (s) Cycle # Time into cycle (s) State M1 PWM (%)  M2 PWM (%)  Stats avgd over Avg Flow (FCCM) Std Flow  Max Flow  Min Flow  Avg Voltage (counts)  Std Voltage Max Voltage Min Voltage Avg Current 1 (counts)  Std Current 1 Max Current 1 Min Current 1 Avg Current 2 (counts)  Std Current 2 Max Current 2 Min Current 2 delT (s)  Pt before state change?\n2019  1 11  14  37  21  18.87 1 0.00  0 0 0 7543  0.00  0.00  0.00.0  576.77  18.64 617 423 215.38  54.35 342 124 700.80  1.01  707 694 10.00\n2019  1 11  14  37  31  28.87 1 10.00 0 0 0 7578  0.00  0.00  0.00.0  575.64  18.17 613 437 216.92  53.91 340 127 700.81  0.91  708 694 10.00\n2019  1 11  14  37  41  38.87 1 20.00 0 0 0 7572  0.00  0.00  0.00.0  576.01  18.74 616 398 216.30  54.04 345 125 700.83  0.94  708 694 10.00\n2019  1 11  14  37  51  48.87 1 30.00 0 0 0 7571  0.00  0.00  0.00.0  575.97  18.24 616 413 215.75  54.77 344 125 700.80  0.98  706 695 10.00\n2019  1 11  14  38  1 58.87 1 40.00 0 0 0 7572  0.00  0.00  0.00.0  576.14  17.88 617 428 216.06  54.61 341 117 700.78  1.01  708 694 10.00\n2019  1 11  14  38  11  68.87 1 50.00 0 0 0 7573  0.00  0.00  0.00.0  575.86  18.32 615 420 217.66  54.92 342 127 700.77  0.95  708 694 10.00\n2019  1 11  14  38  21  78.87 1 60.00 0 0 0 7576  0.00  0.00  0.00.0  576.21  18.71 613 400 217.16  54.27 338 123 700.78  0.98  707 694 10.00\n");
}


