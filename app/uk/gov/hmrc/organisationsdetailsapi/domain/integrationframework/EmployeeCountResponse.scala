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
import uk.gov.hmrc.organisationsdetailsapi.domain.numberofemployees.NumberOfEmployeesRequest

import scala.util.matching.Regex

case class EmployeeCountResponse(
  startDate: Option[String],
  endDate: Option[String],
  references: Option[Seq[PayeReferenceAndCount]]
)

case class PayeReferenceAndCount(
  districtNumber: Option[String],
  payeReference: Option[String],
  counts: Option[Seq[Count]]
)

case class EmployeeCountRequest(startDate: String, endDate: String, references: Seq[PayeReference])

case class PayeReference(districtNumber: String, payeReference: String)

case class Count(dateTaken: Option[String], employeeCount: Option[Int])

object Count {

  val minValue = 1
  val maxValue = 99999999
  val datePattern: Regex = "^[1-2]{1}[0-9]{3}-[0-9]{2}$".r

  def isInRange(value: Int): Boolean =
    value >= minValue && value <= maxValue

  def isInRangeAndWholeNumber(value: Int): Boolean =
    isInRange(value)

  implicit val countFormat: Format[Count] = Format(
    (
      (JsPath \ "dateTaken").readNullable[String](using pattern(datePattern, "Date is in incorrect format")) and
        (JsPath \ "employeeCount").readNullable[Int](using verifying[Int](isInRangeAndWholeNumber))
    )(Count.apply),
    (
      (JsPath \ "dateTaken").writeNullable[String] and
        (JsPath \ "employeeCount").writeNullable[Int]
    )(o => Tuple.fromProductTyped(o))
  )
}

object EmployeeCountResponse {

  val datePattern: Regex =
    "^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$".r
  val districtPattern: Regex = "^[0-9]{3}$".r
  val payeRefPattern: Regex = "^[a-zA-Z0-9]{1,10}$".r

  implicit val referencesFormat: Format[PayeReferenceAndCount] = Format(
    (
      (JsPath \ "districtNumber")
        .readNullable[String](using pattern(districtPattern, "District number is in the incorrect format")) and
        (JsPath \ "payeReference")
          .readNullable[String](using pattern(payeRefPattern, "Paye reference is in the incorrect format")) and
        (JsPath \ "counts").readNullable[Seq[Count]]
    )(PayeReferenceAndCount.apply),
    (
      (JsPath \ "districtNumber").writeNullable[String] and
        (JsPath \ "payeReference").writeNullable[String] and
        (JsPath \ "counts").writeNullable[Seq[Count]]
    )(o => Tuple.fromProductTyped(o))
  )

  implicit val ifEmployeeCountFormat: Format[EmployeeCountResponse] = Format(
    (
      (JsPath \ "startDate").readNullable[String](using pattern(datePattern, "startDate is in the incorrect format")) and
        (JsPath \ "endDate").readNullable[String](using pattern(datePattern, "endDate is in the incorrect format")) and
        (JsPath \ "references").readNullable[Seq[PayeReferenceAndCount]]
    )(EmployeeCountResponse.apply),
    (
      (JsPath \ "startDate").writeNullable[String] and
        (JsPath \ "endDate").writeNullable[String] and
        (JsPath \ "references").writeNullable[Seq[PayeReferenceAndCount]]
    )(o => Tuple.fromProductTyped(o))
  )
}

object EmployeeCountRequest {

  val datePattern: Regex =
    "^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$".r
  val districtPattern: Regex = "^[0-9]{3}$".r
  val payeRefPattern: Regex = "^[a-zA-Z0-9]{1,10}$".r

  implicit val referencesFormat: Format[PayeReference] = Format(
    (
      (JsPath \ "districtNumber")
        .read[String](using pattern(districtPattern, "District number is in the incorrect format")) and
        (JsPath \ "payeReference").read[String](using pattern(payeRefPattern, "Paye reference is in the incorrect format"))
    )(PayeReference.apply),
    (
      (JsPath \ "districtNumber").write[String] and
        (JsPath \ "payeReference").write[String]
    )(o => Tuple.fromProductTyped(o))
  )

  implicit val ifEmployeeCountRequestFormat: Format[EmployeeCountRequest] = Format(
    (
      (JsPath \ "startDate").read[String](using pattern(datePattern, "startDate is in the incorrect format")) and
        (JsPath \ "endDate").read[String](using pattern(datePattern, "endDate is in the incorrect format")) and
        (JsPath \ "references").read[Seq[PayeReference]]
    )(EmployeeCountRequest.apply),
    (
      (JsPath \ "startDate").write[String] and
        (JsPath \ "endDate").write[String] and
        (JsPath \ "references").write[Seq[PayeReference]]
    )(o => Tuple.fromProductTyped(o))
  )

  def createFromRequest(request: NumberOfEmployeesRequest): EmployeeCountRequest =
    EmployeeCountRequest(
      request.fromDate,
      request.toDate,
      request.payeReference.map(x => PayeReference(x.districtNumber, x.schemeReference))
    )
}
