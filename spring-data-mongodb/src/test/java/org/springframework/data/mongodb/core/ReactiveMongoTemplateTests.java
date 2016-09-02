/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.mongodb.core;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mapping.model.MappingException;
import org.springframework.data.mongodb.core.MongoTemplateTests.PersonWithConvertedId;
import org.springframework.data.mongodb.core.MongoTemplateTests.VersionedPerson;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Version;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.WriteConcern;

import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.test.TestSubscriber;

/**
 * Integration test for {@link MongoTemplate}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:reactive-infrastructure.xml")
public class ReactiveMongoTemplateTests {

	@Autowired ReactiveMongoDbFactory factory;
	@Autowired ReactiveMongoTemplate template;

	@Rule public ExpectedException thrown = ExpectedException.none();

	Version mongoVersion;

	@Before
	public void setUp() {
		cleanDb();
		queryMongoVersionIfNecessary();
	}

	@After
	public void cleanUp() {}

	private void queryMongoVersionIfNecessary() {

		if (mongoVersion == null) {
			org.bson.Document result = template.executeCommand("{ buildInfo: 1 }").block();
			mongoVersion = org.springframework.data.util.Version.parse(result.get("version").toString());
		}
	}

	private void cleanDb() {
		template.dropCollection("people").block();
		template.dropCollection("collection").block();
		template.dropCollection(Person.class).block();
		template.dropCollection(PersonWithAList.class).block();
		template.dropCollection(PersonWith_idPropertyOfTypeObjectId.class).block();
		template.dropCollection(PersonWith_idPropertyOfTypeString.class).block();
		template.dropCollection(PersonWithIdPropertyOfTypeObjectId.class).block();
		template.dropCollection(PersonWithIdPropertyOfTypeString.class).block();
		template.dropCollection(PersonWithIdPropertyOfTypeInteger.class).block();
		template.dropCollection(PersonWithIdPropertyOfTypeBigInteger.class).block();
		template.dropCollection(PersonWithIdPropertyOfPrimitiveInt.class).block();
		template.dropCollection(PersonWithIdPropertyOfTypeLong.class).block();
		template.dropCollection(PersonWithIdPropertyOfPrimitiveLong.class).block();
		template.dropCollection(PersonWithVersionPropertyOfTypeInteger.class).block();
		template.dropCollection(BaseDoc.class).block();
		template.dropCollection(Sample.class).block();
	}

	@Test
	public void insertSetsId() throws Exception {

		PersonWithAList person = new PersonWithAList();
		assert person.getId() == null;

		template.insert(person).block();

		assertThat(person.getId(), is(notNullValue()));
	}

	@Test
	public void insertAllSetsId() throws Exception {

		PersonWithAList person = new PersonWithAList();
		assert person.getId() == null;

		template.insertAll(Arrays.asList(person)).next().block();

		assertThat(person.getId(), is(notNullValue()));
	}

	@Test
	public void insertCollectionSetsId() throws Exception {

		PersonWithAList person = new PersonWithAList();
		assert person.getId() == null;

		template.insert(Arrays.asList(person), PersonWithAList.class).next().block();

		assertThat(person.getId(), is(notNullValue()));
	}

	@Test
	public void saveSetsId() throws Exception {

		PersonWithAList person = new PersonWithAList();
		assert person.getId() == null;

		template.save(person).block();

		assertThat(person.getId(), is(notNullValue()));
	}

	@Test
	public void insertsSimpleEntityCorrectly() throws Exception {

		Person person = new Person("Mark");
		person.setAge(35);
		template.insert(person).block();

		TestSubscriber<Person> testSubscriber = TestSubscriber.create();
		Flux<Person> flux = template.find(new Query(Criteria.where("_id").is(person.getId())), Person.class);
		flux.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(1);
		testSubscriber.assertValues(person);
	}

	@Test
	public void simpleInsertDoesNotAllowArrays() throws Exception {

		thrown.expect(IllegalArgumentException.class);

		Person person = new Person("Mark");
		person.setAge(35);
		template.insert(new Person[] { person });
	}

	@Test
	public void simpleInsertDoesNotAllowCollections() throws Exception {

		thrown.expect(IllegalArgumentException.class);

		Person person = new Person("Mark");
		person.setAge(35);
		template.insert(Arrays.asList(person));
	}

	@Test
	public void insertsSimpleEntityWithSuppliedCollectionNameCorrectly() throws Exception {

		Person person = new Person("Homer");
		person.setAge(35);
		template.insert(person, "people").block();

		TestSubscriber<Person> testSubscriber = TestSubscriber.create();
		Flux<Person> flux = template.find(new Query(Criteria.where("_id").is(person.getId())), Person.class, "people");
		flux.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(1);
		testSubscriber.assertValues(person);
	}

	@Test
	public void insertBatchCorrectly() throws Exception {

		List<Person> persons = Arrays.asList(new Person("Dick", 22), new Person("Harry", 23), new Person("Tom", 21));

		template.insertAll(persons).next().block();

		TestSubscriber<Person> testSubscriber = TestSubscriber.create();
		Flux<Person> flux = template.find(new Query().with(new Sort(new Order("firstname"))), Person.class);
		flux.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(3);
		testSubscriber.assertValues(persons.toArray(new Person[persons.size()]));
	}

	@Test
	public void insertBatchWithSuppliedCollectionNameCorrectly() throws Exception {

		List<Person> persons = Arrays.asList(new Person("Dick", 22), new Person("Harry", 23), new Person("Tom", 21));

		template.insert(persons, "people").then().block();

		TestSubscriber<Person> testSubscriber = TestSubscriber.create();
		Flux<Person> flux = template.find(new Query().with(new Sort(new Order("firstname"))), Person.class, "people");
		flux.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(3);
		testSubscriber.assertValues(persons.toArray(new Person[persons.size()]));
	}

	@Test
	public void insertBatchWithSuppliedEntityTypeCorrectly() throws Exception {

		List<Person> persons = Arrays.asList(new Person("Dick", 22), new Person("Harry", 23), new Person("Tom", 21));

		template.insert(persons, Person.class).then().block();

		TestSubscriber<Person> testSubscriber = TestSubscriber.create();
		Flux<Person> flux = template.find(new Query().with(new Sort(new Order("firstname"))), Person.class);
		flux.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(3);
		testSubscriber.assertValues(persons.toArray(new Person[persons.size()]));
	}

	@Test
	public void testAddingToList() {

		PersonWithAList p = new PersonWithAList();
		p.setFirstName("Sven");
		p.setAge(22);
		template.insert(p).block();

		Query q1 = new Query(Criteria.where("id").is(p.getId()));
		PersonWithAList p2 = template.findOne(q1, PersonWithAList.class).block();
		assertThat(p2, notNullValue());
		assertThat(p2.getWishList().size(), is(0));

		p2.addToWishList("please work!");

		template.save(p2).block();

		PersonWithAList p3 = template.findOne(q1, PersonWithAList.class).block();
		assertThat(p3, notNullValue());
		assertThat(p3.getWishList().size(), is(1));

		Friend f = new Friend();
		p.setFirstName("Erik");
		p.setAge(21);

		p3.addFriend(f);
		template.save(p3).block();

		PersonWithAList p4 = template.findOne(q1, PersonWithAList.class).block();
		assertThat(p4, notNullValue());
		assertThat(p4.getWishList().size(), is(1));
		assertThat(p4.getFriends().size(), is(1));

	}

	@Test
	public void testFindOneWithSort() {
		PersonWithAList p = new PersonWithAList();
		p.setFirstName("Sven");
		p.setAge(22);
		template.insert(p).block();

		PersonWithAList p2 = new PersonWithAList();
		p2.setFirstName("Erik");
		p2.setAge(21);
		template.insert(p2).block();

		PersonWithAList p3 = new PersonWithAList();
		p3.setFirstName("Mark");
		p3.setAge(40);
		template.insert(p3).block();

		// test query with a sort
		Query q2 = new Query(Criteria.where("age").gt(10));
		q2.with(new Sort(Direction.DESC, "age"));
		PersonWithAList p5 = template.findOne(q2, PersonWithAList.class).block();
		assertThat(p5.getFirstName(), is("Mark"));
	}

	@Test
	public void bogusUpdateDoesNotTriggerException() throws Exception {

		ReactiveMongoTemplate mongoTemplate = new ReactiveMongoTemplate(factory);
		mongoTemplate.setWriteResultChecking(WriteResultChecking.EXCEPTION);

		Person person = new Person("Oliver2");
		person.setAge(25);
		mongoTemplate.insert(person).block();

		Query q = new Query(Criteria.where("BOGUS").gt(22));
		Update u = new Update().set("firstName", "Sven");
		mongoTemplate.updateFirst(q, u, Person.class).block();
	}

	@Test
	public void throwsExceptionForDuplicateIds() {

		ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
		template.setWriteResultChecking(WriteResultChecking.EXCEPTION);

		Person person = new Person(new ObjectId(), "Amol");
		person.setAge(28);

		template.insert(person).block();

		try {
			template.insert(person).block();
			fail("Expected DataIntegrityViolationException!");
		} catch (DataIntegrityViolationException e) {
			assertThat(e.getMessage(), containsString("E11000 duplicate key error"));
		}
	}

	@Test
	public void throwsExceptionForUpdateWithInvalidPushOperator() {

		ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
		template.setWriteResultChecking(WriteResultChecking.EXCEPTION);

		ObjectId id = new ObjectId();
		Person person = new Person(id, "Amol");
		person.setAge(28);

		template.insert(person).block();

		thrown.expect(DataIntegrityViolationException.class);
		thrown.expectMessage("array");
		thrown.expectMessage("age");
		// thrown.expectMessage("failed");

		Query query = new Query(Criteria.where("firstName").is("Amol"));
		Update upd = new Update().push("age", 29);
		template.updateFirst(query, upd, Person.class).block();
	}

	@Test
	public void rejectsDuplicateIdInInsertAll() {

		thrown.expect(DataIntegrityViolationException.class);
		thrown.expectMessage("E11000 duplicate key error");

		ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
		template.setWriteResultChecking(WriteResultChecking.EXCEPTION);

		ObjectId id = new ObjectId();
		Person person = new Person(id, "Amol");
		person.setAge(28);

		List<Person> records = new ArrayList<Person>();
		records.add(person);
		records.add(person);

		template.insertAll(records).next().block();
	}

	@Test
	public void testFindAndUpdate() {

		template.insertAll(Arrays.asList(new Person("Tom", 21), new Person("Dick", 22), new Person("Harry", 23))).next()
				.block();

		Query query = new Query(Criteria.where("firstName").is("Harry"));
		Update update = new Update().inc("age", 1);

		Person p = template.findAndModify(query, update, Person.class).block(); // return old
		assertThat(p.getFirstName(), is("Harry"));
		assertThat(p.getAge(), is(23));
		p = template.findOne(query, Person.class).block();
		assertThat(p.getAge(), is(24));

		p = template.findAndModify(query, update, Person.class, "person").block();
		assertThat(p.getAge(), is(24));
		p = template.findOne(query, Person.class).block();
		assertThat(p.getAge(), is(25));

		p = template.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), Person.class).block();
		assertThat(p.getAge(), is(26));

		p = template.findAndModify(query, update, null, Person.class, "person").block();
		assertThat(p.getAge(), is(26));
		p = template.findOne(query, Person.class).block();
		assertThat(p.getAge(), is(27));

		Query query2 = new Query(Criteria.where("firstName").is("Mary"));
		p = template.findAndModify(query2, update, new FindAndModifyOptions().returnNew(true).upsert(true), Person.class)
				.block();
		assertThat(p.getFirstName(), is("Mary"));
		assertThat(p.getAge(), is(1));
	}

	@Test
	public void testFindAllAndRemoveFullyReturnsAndRemovesDocuments() {

		Sample spring = new Sample("100", "spring");
		Sample data = new Sample("200", "data");
		Sample mongodb = new Sample("300", "mongodb");
		template.insert(Arrays.asList(spring, data, mongodb), Sample.class).then().block();

		Query qry = query(where("field").in("spring", "mongodb"));

		TestSubscriber<Sample> testSubscriber = TestSubscriber.create();
		template.findAllAndRemove(qry, Sample.class).subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(2);
		testSubscriber.assertValues(spring, mongodb);

		assertThat(template.findOne(new Query(), Sample.class).block(), is(equalTo(data)));
	}

	@Test(expected = OptimisticLockingFailureException.class)
	public void optimisticLockingHandling() {

		// Init version
		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.age = 29;
		person.firstName = "Patryk";
		template.save(person).block();

		List<PersonWithVersionPropertyOfTypeInteger> result = Flux
				.from(template.findAll(PersonWithVersionPropertyOfTypeInteger.class)).collectList().block();

		assertThat(result, hasSize(1));
		assertThat(result.get(0).version, is(0));

		// Version change
		person = result.get(0);
		person.firstName = "Patryk2";

		template.save(person).block();

		assertThat(person.version, is(1));

		result = Flux.from(template.findAll(PersonWithVersionPropertyOfTypeInteger.class)).collectList().block();

		assertThat(result, hasSize(1));
		assertThat(result.get(0).version, is(1));

		// Optimistic lock exception
		person.version = 0;
		person.firstName = "Patryk3";

		template.save(person).block();
	}

	@Test
	public void optimisticLockingHandlingWithExistingId() {

		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.id = new ObjectId().toString();
		person.age = 29;
		person.firstName = "Patryk";
		template.save(person);
	}

	@Test
	public void doesNotFailOnVersionInitForUnversionedEntity() {

		org.bson.Document dbObject = new org.bson.Document();
		dbObject.put("firstName", "Oliver");

		template.insert(dbObject, template.determineCollectionName(PersonWithVersionPropertyOfTypeInteger.class));
	}

	@Test
	public void removesObjectFromExplicitCollection() {

		String collectionName = "explicit";
		template.remove(new Query(), collectionName).block();

		PersonWithConvertedId person = new PersonWithConvertedId();
		person.name = "Dave";
		template.save(person, collectionName).block();
		assertThat(template.findAll(PersonWithConvertedId.class, collectionName).next().block(), is(notNullValue()));

		template.remove(person, collectionName).block();
		assertThat(template.findAll(PersonWithConvertedId.class, collectionName).next().block(), is(nullValue()));
	}

	@Test
	public void savesMapCorrectly() {

		Map<String, String> map = new HashMap<String, String>();
		map.put("key", "value");

		template.save(map, "maps").block();
	}

	@Test(expected = MappingException.class)
	public void savesMongoPrimitiveObjectCorrectly() {
		template.save(new Object(), "collection").block();
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullObjectToBeSaved() {
		template.save((Object) null);
	}

	@Test
	public void savesPlainDbObjectCorrectly() {

		org.bson.Document dbObject = new org.bson.Document("foo", "bar");
		template.save(dbObject, "collection").block();

		assertThat(dbObject.containsKey("_id"), is(true));
	}

	@Test(expected = InvalidDataAccessApiUsageException.class)
	public void rejectsPlainObjectWithOutExplicitCollection() {

		org.bson.Document dbObject = new org.bson.Document("foo", "bar");
		template.save(dbObject, "collection").block();

		template.findById(dbObject.get("_id"), org.bson.Document.class).block();
	}

	@Test
	public void readsPlainDbObjectById() {

		org.bson.Document dbObject = new org.bson.Document("foo", "bar");
		template.save(dbObject, "collection").block();

		org.bson.Document result = template.findById(dbObject.get("_id"), org.bson.Document.class, "collection").block();
		assertThat(result.get("foo"), is(dbObject.get("foo")));
		assertThat(result.get("_id"), is(dbObject.get("_id")));
	}

	@Test
	public void writesPlainString() {
		template.save("{ 'foo' : 'bar' }", "collection").block();
	}

	@Test(expected = MappingException.class)
	public void rejectsNonJsonStringForSave() {
		template.save("Foobar!", "collection").block();
	}

	@Test
	public void initializesVersionOnInsert() {

		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.firstName = "Dave";

		template.insert(person).block();

		assertThat(person.version, is(0));
	}

	@Test
	public void initializesVersionOnBatchInsert() {

		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.firstName = "Dave";

		template.insertAll(Arrays.asList(person)).next().block();

		assertThat(person.version, is(0));
	}

	@Test
	public void queryCantBeNull() {

		List<PersonWithIdPropertyOfTypeObjectId> result = Flux
				.from(template.findAll(PersonWithIdPropertyOfTypeObjectId.class)).collectList().block();
		assertThat(template.find(null, PersonWithIdPropertyOfTypeObjectId.class).collectList().block(), is(result));
	}

	@Test
	public void versionsObjectIntoDedicatedCollection() {

		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.firstName = "Dave";

		template.save(person, "personX").block();
		assertThat(person.version, is(0));

		template.save(person, "personX").block();
		assertThat(person.version, is(1));
	}

	@Test
	public void correctlySetsLongVersionProperty() {

		PersonWithVersionPropertyOfTypeLong person = new PersonWithVersionPropertyOfTypeLong();
		person.firstName = "Dave";

		template.save(person).block();
		assertThat(person.version, is(0L));
	}

	@Test
	public void throwsExceptionForIndexViolationIfConfigured() {

		ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
		template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
		template.indexOps(Person.class).ensureIndex(new Index().on("firstName", Direction.DESC).unique()).block();

		Person person = new Person(new ObjectId(), "Amol");
		person.setAge(28);

		template.save(person).block();

		person = new Person(new ObjectId(), "Amol");
		person.setAge(28);

		try {
			template.save(person).block();
			fail("Expected DataIntegrityViolationException!");
		} catch (DataIntegrityViolationException e) {
			assertThat(e.getMessage(), containsString("E11000 duplicate key error"));
		}
	}

	@Test(expected = DuplicateKeyException.class)
	public void preventsDuplicateInsert() {

		template.setWriteConcern(WriteConcern.MAJORITY);

		PersonWithVersionPropertyOfTypeInteger person = new PersonWithVersionPropertyOfTypeInteger();
		person.firstName = "Dave";

		template.save(person).block();
		assertThat(person.version, is(0));

		person.version = null;
		template.save(person).block();
	}

	@Test
	public void countAndFindWithoutTypeInformation() {

		Person person = new Person();
		template.save(person).block();

		Query query = query(where("_id").is(person.getId()));
		String collectionName = template.getCollectionName(Person.class);

		assertThat(Flux.from(template.find(query, HashMap.class, collectionName)).collectList().block(), hasSize(1));
		assertThat(template.count(query, collectionName).block(), is(1L));
	}

	@Test
	public void nullsPropertiesForVersionObjectUpdates() {

		VersionedPerson person = new VersionedPerson();
		person.firstname = "Dave";
		person.lastname = "Matthews";

		template.save(person).block();
		assertThat(person.id, is(notNullValue()));

		person.lastname = null;
		template.save(person).block();

		person = template.findOne(query(where("id").is(person.id)), VersionedPerson.class).block();
		assertThat(person.lastname, is(nullValue()));
	}

	@Test
	public void nullsValuesForUpdatesOfUnversionedEntity() {

		Person person = new Person("Dave");
		template.save(person).block();

		person.setFirstName(null);
		template.save(person).block();

		person = template.findOne(query(where("id").is(person.getId())), Person.class).block();
		assertThat(person.getFirstName(), is(nullValue()));
	}

	@Test
	public void savesJsonStringCorrectly() {

		org.bson.Document dbObject = new org.bson.Document().append("first", "first").append("second", "second");

		template.save(dbObject, "collection").block();

		org.bson.Document result = template.findAll(org.bson.Document.class, "collection").next().block();
		assertThat(result.containsKey("first"), is(true));
	}

	@Test
	public void executesExistsCorrectly() {

		Sample sample = new Sample();
		template.save(sample).block();

		Query query = query(where("id").is(sample.id));

		assertThat(template.exists(query, Sample.class).block(), is(true));
		assertThat(template.exists(query(where("_id").is(sample.id)), template.getCollectionName(Sample.class)).block(),
				is(true));
		assertThat(template.exists(query, Sample.class, template.getCollectionName(Sample.class)).block(), is(true));
	}

	@Data
	static class Sample {

		@Id String id;
		String field;

		public Sample() {}

		public Sample(String id, String field) {
			this.id = id;
			this.field = field;
		}
	}

}
