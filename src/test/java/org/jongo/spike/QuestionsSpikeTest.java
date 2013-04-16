/*
 * Copyright (C) 2011 Benoit GUEROUT <bguerout at gmail dot com> and Yves AMSELLEM <amsellem dot yves at gmail dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jongo.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MapperFeature;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteResult;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.ResultHandler;
import org.jongo.marshall.jackson.JacksonEngine;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.configuration.Mapping;
import org.jongo.model.Friend;
import org.jongo.util.JSONResultHandler;
import org.jongo.util.JongoTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class QuestionsSpikeTest extends JongoTestCase {

    private MongoCollection collection;

    @Before
    public void setUp() throws Exception {
        collection = createEmptyCollection("friends");
    }

    @After
    public void tearDown() throws Exception {
        dropCollection("friends");
    }

    @Test
    // http://stackoverflow.com/questions/10444038/mongo-db-query-in-java/10445169#10445169
    public void complexQueryWithDriverAndJongo() throws Exception {

        List<String> keys = new ArrayList<String>();
        collection.findOne("{$or:[{key1: {$in:[764]}},{key2:{$in:[#]}}, {$and:[{key3:3},{key4:67}]}]}", keys).as(Friend.class);

        DBObject query = QueryBuilder
                .start()
                .or(QueryBuilder.start("key1").in(764).get(), QueryBuilder.start("key2").in(keys).get(),
                        QueryBuilder.start().and("key3").is(3).and("key4").is(64).get()).get();

        getDatabase().getCollection("friends").find(query);
    }

    @Test
    // https://groups.google.com/forum/?hl=fr&fromgroups#!topic/jongo-user/ga3n5_ybYm4
    public void pushANonBSONObject() throws Exception {
        Friends friends = new Friends();
        friends.add(new Friend("john"));
        friends.add(new Friend("peter"));
        collection.save(friends);

        DBObject robert = new JacksonEngine(Mapping.defaultMapping()).marshall(new Friend("Robert")).toDBObject();
        collection.update("{}").with("{$push:{friends:" + robert.toString() + "}}");

        assertThat(collection.count("{ 'friends.name' : 'Robert'}")).isEqualTo(1);
    }

    @Test
    // https://groups.google.com/forum/?fromgroups=#!topic/jongo-user/UVOEmP-ql_k
    public void canHandleElemMatchOperator() throws Exception {

        assumeThatMongoVersionIsGreaterThan("2.1.1");

        collection.insert("{version : 1, days:[{name:'monday'},{name:'sunday'}]}");
        collection.insert("{version : 2, days:[{name:'wednesday'}]}");

        String monday = collection.findOne("{version:1}").projection("{days:{$elemMatch:{name: 'monday'}}}").map(new JSONResultHandler());

        assertThat(monday).contains("\"days\" : [ { \"name\" : \"monday\"}]");
    }

    @Test
    public void testJacksonProblem() throws Exception {
        System.out.println("pojo jongo test");

        JacksonMapper.Builder builder = new JacksonMapper.Builder()
                .enable(MapperFeature.USE_ANNOTATIONS)
                .enable(MapperFeature.AUTO_DETECT_GETTERS)
                .enable(MapperFeature.AUTO_DETECT_SETTERS);


        Jongo jongo = new Jongo(getDatabase(), builder.build());
        MongoCollection jongoTestCollection = jongo.getCollection("friends");

        PojoTestDocument pojoTestDocument = new PojoTestDocument();
        pojoTestDocument.setAccessorJSONProperty("accessorValue");
        jongoTestCollection.save(pojoTestDocument);

        jongoTestCollection.findOne().map(new ResultHandler<DBObject>() {
            public DBObject map(DBObject result) {
                assertThat(result.get("privateProperty")).isNull();
                assertThat(result.get("accessorJSONProperty")).isEqualTo("accessorValue");
                return result;
            }
        });

    }

    private static class Friends {
        private List<Friend> friends = new ArrayList<Friend>();

        public void add(Friend buddy) {
            friends.add(buddy);
        }
    }

    private static class PojoTestDocument {

        /**
         * Empty constructor required by Jackson mapper
         */
        public PojoTestDocument() {
        }

        /**
         * Primary unique key mongo, generated upon insert
         */
        private ObjectId _id;

        @JsonIgnore
        private String privateProperty= "privateValue";

        @JsonIgnore
        protected String protectedProperty= "protectedValue";

        public String publicProperty = "publicValue";

        public String emptyPublicProperty;

        @JsonIgnore
        protected String accessorProperty;

        @JsonProperty("accessorJSONProperty")
        public String getAccessorJSONProperty() {
            return accessorProperty;
        }

        public void setAccessorJSONProperty(String a) {
            this.accessorProperty = a;
        }

    }
}
