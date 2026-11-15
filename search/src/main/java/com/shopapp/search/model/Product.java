package com.shopapp.search.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * The indexed product (YAS search {@code Product}). Brand is a keyword (YAS: text with fielddata) so a filter or a
 * facet matches the whole brand name. {@code version} is product's last modification time in microseconds, written as
 * an {@code external_gte} version: an older read of the product can never overwrite a newer one (decisions.md 4-13).
 */
@Document(indexName = Product.INDEX, versionType = Document.VersionType.EXTERNAL_GTE)
@Setting(settingPath = "esconfig/elastic-analyzer.json")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    public static final String INDEX = "product";

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Version
    private Long version;

    @Field(type = FieldType.Text, analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Boolean)
    private Boolean isPublished;

    @Field(type = FieldType.Boolean)
    private Boolean isVisibleIndividually;

    @Field(type = FieldType.Boolean)
    private Boolean isAllowedToOrder;

    @Field(type = FieldType.Boolean)
    private Boolean isFeatured;

    @Field(type = FieldType.Long)
    private Long thumbnailMediaId;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private List<String> categories;

    @Field(type = FieldType.Keyword)
    private List<String> attributes;

    @Field(type = FieldType.Date, format = {DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant createdOn;
}
