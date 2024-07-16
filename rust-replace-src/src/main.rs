#![crate_type = "cdylib"]
use base64::decode;
use std::env;
use std::io::{self, Read};

fn main() {
    let mut args: Vec<String> = env::args().collect();
    let mut input_buffer = String::new();

    io::stdin()
        .read_to_string(&mut input_buffer)
        .expect("Failed to read input");

    let mut work_string = input_buffer;

    let mut i = 1; // Start from 1 to skip the program name
    while i < args.len() {
        let from = &args[i];
        let to = &args[i + 1];
        i += 2;

        let from_decoded = decode(from).expect("Failed to decode base64 'from'");
        let to_decoded = decode(to).expect("Failed to decode base64 'to'");

        let from_str = String::from_utf8(from_decoded).expect("Failed to convert 'from' to UTF-8");
        let to_str = String::from_utf8(to_decoded).expect("Failed to convert 'to' to UTF-8");

        work_string = work_string.replace(&from_str, &to_str);
    }

    println!("{}", work_string);
}
