/*
 * Copyright 2025 HM Revenue & Customs
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

package unit.uk.gov.hmrc.organisationsdetailsapi.cache



import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Format, JsString, JsValue, Json}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.organisationsdetailsapi.cache.ModifiedDetails

import java.time.Instant


class ModifiedDetailsSpec extends AnyWordSpec with Matchers {
  given Format[Instant] = MongoJavatimeFormats.instantFormat

  "ModifiedDetails JSON format" should {

    "serialize ModifiedDetails to JSON correctly" in {
      val now = Instant.parse("2025-11-04T14:33:11Z")
      val details = ModifiedDetails(createdAt = now, lastUpdated= now)
      val json = Json.toJson(details)

      (json \ "createdAt").as[Instant] shouldBe  now
      (json \ "lastUpdated").as[Instant] shouldBe now

    }

    "deserialize JSON to ModifiedDetails correctly" in {
      val now = Instant.parse("2023-05-10T12:30:00Z")

      val json = Json.obj(
        "createdAt" -> Json.toJson(now),
        "lastUpdated" -> Json.toJson(now)
      )

      val result = json.as[ModifiedDetails]
      result.createdAt shouldBe now
      result.lastUpdated shouldBe now
    }

    "fail to deserialize if fields are missing" in {
      val json = Json.obj("createdAt" -> Json.toJson(Instant.now()))

      val result = Json.fromJson[ModifiedDetails](json)
      result.isError shouldBe true
    }

    "fail to deserialize if fields are invalid" in {
      val json = Json.obj(
        "createdAt" -> JsString("not-an-instant"),
        "lastUpdated" -> JsString("still-not-an-instant")
      )

      val result = Json.fromJson[ModifiedDetails](json)
      result.isError shouldBe true
    }
  }
}