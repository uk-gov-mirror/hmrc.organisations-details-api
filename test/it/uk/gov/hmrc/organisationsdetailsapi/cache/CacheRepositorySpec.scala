/*
 * Copyright 2023 HM Revenue & Customs
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

package it.uk.gov.hmrc.organisationsdetailsapi.cache

import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import org.mongodb.scala.SingleObservableFuture
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.organisationsdetailsapi.cache.CacheRepository
import utils.TestSupport

import java.util.UUID
import scala.concurrent.ExecutionContext

class CacheRepositorySpec
  extends AsyncWordSpec
    with Matchers
    with BeforeAndAfterEach
    with MongoSupport
    with TestSupport {

  private val cacheTtl = 60
  private val id = UUID.randomUUID().toString
  private val testValue = TestClass("one", "two")

  lazy val fakeApplication = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> mongoUri, "cache.ttlInSeconds" -> cacheTtl)
    .bindings(Seq()*)
    .build()

  private val shortLivedCache = fakeApplication.injector.instanceOf[CacheRepository]
  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  def externalServices: Seq[String] = Seq.empty

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(shortLivedCache.collection.drop().toFuture())
  }

  override def afterEach(): Unit = {
    super.afterEach()
    await(shortLivedCache.collection.drop().toFuture())
  }

  "cache" should {
    "store the encrypted version of a value" in {
      shortLivedCache.cache(id, testValue)(using TestClass.format) map { _ =>
        retrieveRawCachedValue(id) shouldBe JsString("6aZpkTxkw3C4e5xTyfy3Lf/OZOFz+GcaSkeFI++0HOs=")
      }
    }

    "update a cached value for a given id and key" in {
      val newValue = TestClass("three", "four")

      await(shortLivedCache.cache(id, testValue)(using TestClass.format))
      retrieveRawCachedValue(id) shouldBe JsString("6aZpkTxkw3C4e5xTyfy3Lf/OZOFz+GcaSkeFI++0HOs=")

      await(shortLivedCache.cache(id, newValue)(using TestClass.format))
      retrieveRawCachedValue(id) shouldBe JsString("8jVeGr+Ivyk5mkBj2VsQE3G+oPGXoYejrSp5hfVAPYU=")
    }
  }

  "fetch" should {
    "retrieve the unencrypted cached value for a given id and key" in {
      shortLivedCache.cache(id, testValue)(using TestClass.format) flatMap { _ =>
        shortLivedCache.fetchAndGetEntry[TestClass](id)(using TestClass.format) map { value =>
          value shouldBe Some(testValue)
        }
      }
    }

    "return None if no cached value exists for a given id and key" in {
      shortLivedCache.fetchAndGetEntry[TestClass](id)(using TestClass.format) map { value =>
        value shouldBe None
      }
    }
  }

  private def retrieveRawCachedValue(id: String) = {
    await(shortLivedCache.collection.find(Filters.equal("id", toBson(id)))
      .headOption()
      .map {
        case Some(entry) => entry.data.value
        case None => None
      })
  }

  case class TestClass(one: String, two: String)

  object TestClass {
    implicit val format: OFormat[TestClass] = Json.format[TestClass]
  }

}
