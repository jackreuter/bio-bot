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
    //sendFile("190502_A.TXT","\n\nBox ID:\tTESTBOX1\n\nSubsampling interval: \t5 min\nSubsampling duration: \t30 s\nPrepumping time: \t20 s\nPostpumping time: \t10 s\nTotal sampling duration: \t24 hr\nSleep time: \t23:00 to 6:00\nWait time after reset: \t5 min\nCurrent Time: \tThursday, May 2, 2019 16:37:17\n\nYear\tMonth\tDay\tHour\tMinute\tSecond\tTime into program (s)\tCycle #\tTime into cycle (s)\tState\tM1 PWM (%)\tM2 PWM (%)\tStats avgd over\tAvg Flow (SCCM)\tStd Flow\tMax Flow\tMin Flow\tAvg Pressure (counts)\tStd Pressure\tMax Pressure\tMin Pressure\tAvg Voltage (counts)\tStd Voltage\tMax Voltage\tMin Voltage\tAvg Current 1 (counts)\tStd Current 1\tMax Current 1\tMin Current 1\tAvg Current 2 (counts)\tStd Current 2\tMax Current 2\tMin Current 2\tTotal flow (mL)\tdelT (s)\tPt before state change?\n\n2019\t5\t2\t16\t37\t17\t93.93\t0\t0.00\t0\t0\t0\t16503\t12.00\t0.00\t12.0\t12.0\t1.00\t0.04\t2\t1\t3880.01\t3.41\t3884\t3875\t1.00\t0.00\t1\t1\t9.60\t0.49\t10\t9\t2.00\t10.00\n\n2019\t5\t2\t16\t37\t27\t103.93\t0\t10.00\t0\t0\t0\t17353\t12.00\t0.00\t12.0\t12.0\t1.00\t0.05\t2\t1\t1984.77\t1700.40\t3884\t306\t1.00\t0.00\t1\t1\t9.23\t0.47\t10\t8\t4.00\t10.00\n\n2019\t5\t2\t16\t37\t37\t113.93\t0\t20.00\t0\t0\t0\t\n\n2019\t5\t2\t17\t9\t40\t2.42\t0\t0.00\t0\t0\t0\t17371\t12.00\t0.00\t12.0\t12.0\t1.00\t0.00\t1\t1\t12.67\t4.63\t24\t2\t1.00\t0.00\t1\t1\t7.93\t0.25\t9\t7\t2.00\t10.00\n");  
    sendFile("190111_I.TXT","TONS OF TEXT FAKE DATA BLAH BLAH BLAH");
    //sendFile("190117_A.TXT","MORE FAKE TEXT BLAH BLAH TEXT BLAH");
}


