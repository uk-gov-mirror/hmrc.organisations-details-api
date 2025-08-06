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
import play.api.libs.json.{Format, JsPath}

import scala.util.matching.Regex

case class AccountingPeriod(apStartDate: Option[String], apEndDate: Option[String], turnover: Option[Int])

case class CorporationTaxReturnDetailsResponse(
  utr: Option[String],
  taxpayerStartDate: Option[String],
  taxSolvencyStatus: Option[String],
  accountingPeriods: Option[Seq[AccountingPeriod]]
)

object CorporationTaxReturnDetails {

  val apDatePattern: Regex =
    "^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$".r
  var utrPattern: Regex = "^[0-9]{10}$".r
  val taxpayerStartDatePattern: Regex =
    "^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$".r

  def validTaxSolvencyStatus(value: String): Boolean = Seq("V", "S", "I", "A").contains(value)

  implicit val accountingPeriodFormat: Format[AccountingPeriod] = Format[AccountingPeriod](
    (
      (JsPath \ "apStartDate").readNullable[String](using pattern(apDatePattern, "apStartDate not in correct format")) and
        (JsPath \ "apEndDate").readNullable[String](using pattern(apDatePattern, "apEndDate not in correct format")) and
        (JsPath \ "turnover").readNullable[Int]
    )(AccountingPeriod.apply),
    (
      (JsPath \ "apStartDate").writeNullable[String] and
        (JsPath \ "apEndDate").writeNullable[String] and
        (JsPath \ "turnover").writeNullable[Int]
    )(o => Tuple.fromProductTyped(o))
  )

  implicit val corporationTaxReturnDetailsResponseFormat: Format[CorporationTaxReturnDetailsResponse] =
    Format[CorporationTaxReturnDetailsResponse](
      (
        (JsPath \ "utr").readNullable[String](using pattern(utrPattern, "Invalid UTR format")) and
          (JsPath \ "taxpayerStartDate")
            .readNullable[String](using pattern(taxpayerStartDatePattern, "Invalid taxpayer start date")) and
          (JsPath \ "taxSolvencyStatus").readNullable[String](using verifying(validTaxSolvencyStatus)) and
          (JsPath \ "accountingPeriods").readNullable[Seq[AccountingPeriod]]
      )(CorporationTaxReturnDetailsResponse.apply),
      (
        (JsPath \ "utr").writeNullable[String] and
          (JsPath \ "taxpayerStartDate").writeNullable[String] and
          (JsPath \ "taxSolvencyStatus").writeNullable[String] and
          (JsPath \ "accountingPeriods").writeNullable[Seq[AccountingPeriod]]
      )(o => Tuple.fromProductTyped(o))
    )
}
