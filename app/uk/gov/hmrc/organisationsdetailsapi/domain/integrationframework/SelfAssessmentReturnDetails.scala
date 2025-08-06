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

package uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.util.matching.Regex

case class TaxYear(taxyear: Option[String], businessSalesTurnover: Option[Double])

case class SelfAssessmentReturnDetailResponse(
  utr: Option[String],
  startDate: Option[String],
  taxPayerType: Option[String],
  taxSolvencyStatus: Option[String],
  taxYears: Option[Seq[TaxYear]]
)

object SelfAssessmentReturnDetail {

  val taxYearPattern: Regex = "^20[0-9]{2}$".r
  val utrPattern: Regex = "^[0-9]{10}$".r
  val taxPayerTypePattern: Regex = "^[A-Z][a-zA-Z]{3,24}$".r
  val datePattern: Regex =
    "^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$".r

  def taxSolvencyStatusValidator(value: String): Boolean = value == "S" || value == "I"

  implicit val taxYearFormat: Format[TaxYear] = Format(
    (
      (JsPath \ "taxyear").readNullable[String](using pattern(taxYearPattern, "Tax Year is in the incorrect Format")) and
        (JsPath \ "businessSalesTurnover").readNullable[Double]
    )(TaxYear.apply),
    (
      (JsPath \ "taxyear").writeNullable[String] and
        (JsPath \ "businessSalesTurnover").writeNullable[Double]
    )(o => Tuple.fromProductTyped(o))
  )

  implicit val selfAssessmentResponseFormat: Format[SelfAssessmentReturnDetailResponse] = Format(
    (
      (JsPath \ "utr").readNullable[String](using pattern(utrPattern, "UTR pattern is incorrect")) and
        (JsPath \ "startDate").readNullable[String](using pattern(datePattern, "Date pattern is incorrect")) and
        (JsPath \ "taxpayerType").readNullable[String](using pattern(taxPayerTypePattern, "Invalid taxpayer type")) and
        (JsPath \ "taxSolvencyStatus").readNullable[String](using verifying(taxSolvencyStatusValidator)) and
        (JsPath \ "taxyears").readNullable[Seq[TaxYear]]
    )(SelfAssessmentReturnDetailResponse.apply),
    (
      (JsPath \ "utr").writeNullable[String] and
        (JsPath \ "startDate").writeNullable[String] and
        (JsPath \ "taxpayerType").writeNullable[String] and
        (JsPath \ "taxSolvencyStatus").writeNullable[String] and
        (JsPath \ "taxyears").writeNullable[Seq[TaxYear]]
    )(o => Tuple.fromProductTyped(o))
  )
}
