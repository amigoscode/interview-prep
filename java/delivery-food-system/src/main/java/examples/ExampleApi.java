package examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("test")
public class ExampleApi {

    @Autowired
    ExampleMongoDB exampleMongoDB;
    @Autowired
    ExampleKafka exampleKafka;

    // localhost:8080/test/mongo
    @GetMapping("mongo")
    public ResponseEntity<String> testMongoDB() {
        exampleMongoDB.testRepository();
        return new ResponseEntity<>("MongoDB test done", HttpStatus.OK);
    }

    // localhost:8080/test/kafka
    @GetMapping("kafka")
    public ResponseEntity<String> testKafka() {
        exampleKafka.produceMessage();
        return new ResponseEntity<>("Kafka test done", HttpStatus.OK);
    }

}
