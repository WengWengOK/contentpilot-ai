package com.contentops.ai.capability.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JSON Schema 加载器。
 *
 * <p>从 classpath:{@code schemas/} 目录加载 JSON Schema 文件（Draft-07），
 * 使用 {@link ResourceLoader} 读取并缓存已加载的 schema，避免重复 IO。</p>
 *
 * <p>用法：{@code JsonSchema schema = jsonSchemaLoader.load("topic-suggestion");}
 * 将读取 {@code classpath:schemas/topic-suggestion.json}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSchemaLoader {

    private static final String SCHEMA_LOCATION_PATTERN = "classpath:schemas/%s.json";
    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /** schema 缓存（按名称） */
    private final ConcurrentMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();
    /** 原始 JsonNode 缓存，供修复时回填 prompt 使用 */
    private final ConcurrentMap<String, JsonNode> nodeCache = new ConcurrentHashMap<>();

    /**
     * 加载指定名称的 JSON Schema。
     *
     * @param schemaName schema 文件名（不含扩展名），如 "topic-suggestion"
     * @return networknt {@link JsonSchema}
     */
    public JsonSchema load(String schemaName) {
        return schemaCache.computeIfAbsent(schemaName, name -> {
            JsonNode node = loadNode(name);
            JsonSchema schema = FACTORY.getSchema(node);
            log.debug("已加载JSON Schema: {}", name);
            return schema;
        });
    }

    /**
     * 加载指定名称 schema 的原始 {@link JsonNode}（用于 LLM 修复时回填 schema 文本）。
     */
    public JsonNode loadNode(String schemaName) {
        return nodeCache.computeIfAbsent(schemaName, this::readNode);
    }

    private JsonNode readNode(String schemaName) {
        String location = String.format(SCHEMA_LOCATION_PATTERN, schemaName);
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("JSON Schema文件不存在: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("读取JSON Schema失败: " + location, e);
        }
    }
}
