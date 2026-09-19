package examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

@Component
public class ExampleMongoDB {

    @Autowired
    DummyDocumentRepository repository;

    public void testRepository() {
        repository.deleteAll();
        repository.save(new DummyDocument("test-data"));
        repository.findAll().forEach(System.out::println);
    }
}

@Repository
interface DummyDocumentRepository extends MongoRepository<DummyDocument, String> {}

@Document
class DummyDocument {

    @Id
    private String id;
    private String data;

    public DummyDocument() {}

    public DummyDocument(String data) {
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}