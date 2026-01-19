/* package com.exemple.transactionservice.web;

import org.springframework.web.bind.annotation.RestController;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.web.bind.annotation.GetMapping;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;  
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.web.bind.annotation.CrossOrigin;


@RestController
@CrossOrigin("*")
public class VectorStoreController {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore embeddingStore;
    private JdbcTemplate jdbcTemplate;

    public VectorStoreController(EmbeddingModel embeddingModel, EmbeddingStore embeddingStore, JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.jdbcTemplate = jdbcTemplate;
    }


    @GetMapping("/getEmbeddings")
    public List<String> getAllEmbeddings(String query){
        Embedding content = embeddingModel.embed(query).content();
        System.out.println(content);
        EmbeddingSearchResult search = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(content)
                .build());
        return search.matches().stream().map(m->m.toString()).toList();
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documentList(){
        List<Map<String, Object>> respo = this.jdbcTemplate.query("SELECT * from public.data_vs", new Object[]{}, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap();
            map.put("embedding_id", rs.getObject("embedding_id"));
            map.put("text", rs.getObject("text"));
            map.put("embedding", rs.getObject("embedding"));
            map.put("metadata", rs.getObject("metadata"));
            return map;
        });
        return respo;
    }


    
}
 */