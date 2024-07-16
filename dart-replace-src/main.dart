import 'dart:convert';
import 'dart:io';

void main(List<String> args) {
  StringBuffer inputBuffer = StringBuffer();
  stdin.transform(const Utf8Decoder()).listen((String data) {
    inputBuffer.write(data);
  }).onDone(() {
    String workString = inputBuffer.toString();

    for (int i = 0; i < args.length; i++) {
      String from = args[i];
      String to = args[i + 1];
      i++;

      String fromDecoded = utf8.decode(base64.decode(from));
      String toDecoded = utf8.decode(base64.decode(to));

      workString = workString.replaceAll(fromDecoded, toDecoded);
    }

    print(workString);
  });
}
