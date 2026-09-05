package com.example.mediapagination.infrastructure.mysql;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCommandMapperXmlTest {

    @Test
    void commandMapperXmlParsesAndRegistersEveryWriteStatement() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(
                "mapper/MediaCommandMapper.xml")) {
            new XMLMapperBuilder(input, configuration,
                    "mapper/MediaCommandMapper.xml", configuration.getSqlFragments())
                    .parse();
        }

        String namespace = MediaCommandMapper.class.getName();
        assertThat(configuration.hasStatement(namespace + ".insert")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".updateStatus")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".updateCategory")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".updatePublishTime")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".insertSeedBatch")).isTrue();
    }
}
