import java.lang.annotation.Annotation
import java.util
import java.lang.reflect.Type
import java.util.Iterator
import java.util.function.BiFunction

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.introspect.Annotated
import io.swagger.v3.core.converter._
import io.swagger.v3.oas.models.media._
import io.swagger.scala.converter.SwaggerScalaModelConverter
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}
import models._

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ModelPropertyParserTest extends FlatSpec with Matchers {
  it should "verify swagger-core bug 814" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[CoreBug814])
    val model = schemas.get("CoreBug814")
    model should not be (null)
    val isFoo = model.getProperties().get("isFoo")
    isFoo should not be (null)
    isFoo shouldBe a[BooleanSchema]
  }

  it should "process Option[String] as string" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionString]).asScala.toMap
    val model = schemas.get("ModelWOptionString")
    model should be('defined)
    val stringOpt = model.get.getProperties().get("stringOpt")
    stringOpt should not be (null)
    stringOpt.isInstanceOf[StringSchema] should be(true)
    nullSafeList(stringOpt.getRequired) shouldBe empty
    val stringWithDataType = model.get.getProperties().get("stringWithDataTypeOpt")
    stringWithDataType should not be (null)
    stringWithDataType shouldBe a [StringSchema]
    nullSafeList(stringWithDataType.getRequired) shouldBe empty
  }

  it should "process Option[Model] as Model" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionModel]).asScala.toMap
    val model = schemas.get("ModelWOptionModel")
    model should be ('defined)
    val modelOpt = model.get.getProperties().get("modelOpt")
    modelOpt should not be (null)
    modelOpt.get$ref() shouldEqual "#/components/schemas/ModelWOptionString"
  }

  it should "process Model with Scala BigDecimal as Number" in {
    case class TestModelWithBigDecimal(field: BigDecimal)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigDecimal]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigDecimal")
    model should be ('defined)
    val modelOpt = model.get.getProperties().get("field")
    modelOpt shouldBe a [NumberSchema]
    //nullSafeList(modelOpt.getRequired) should not be empty
  }

  it should "process Model with Scala BigInt as Number" in {
    case class TestModelWithBigInt(field: BigInt)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigInt]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigInt")
    model should be ('defined)
    val modelOpt = model.get.getProperties().get("field")
    modelOpt shouldBe a [IntegerSchema]
    //nullSafeList(modelOpt.getRequired) should not be empty
  }

  it should "process Model with Scala Option BigDecimal" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigDecimal]).asScala.toMap
    val model = schemas.get("ModelWOptionBigDecimal")
    model should be ('defined)
    val optBigDecimal = model.get.getProperties().get("optBigDecimal")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [NumberSchema]
    //nullSafeList(optBigDecimal.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option BigInt" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigInt]).asScala.toMap
    val model = schemas.get("ModelWOptionBigInt")
    model should be ('defined)
    val optBigDecimal = model.get.getProperties().get("optBigInt")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [IntegerSchema]
    //nullSafeList(optBigDecimal.getRequired) shouldBe empty
  }

  it should "process all properties as required barring Option[_] or if overridden in annotation" in {
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption")
    model should not be (null)

    val optional = model.getProperties().get("optional")
    // nullSafeList(optional.getRequired) shouldBe empty

    val required = model.getProperties().get("required")
    // nullSafeList(required.getRequired) should not be empty

    val forcedRequired = model.getProperties().get("forcedRequired")
    // nullSafeList(forcedRequired.getRequired) should not be empty

    val forcedOptional = model.getProperties().get("forcedOptional")
    // nullSafeList(forcedOptional.getRequired) shouldBe empty
  }

  it should "handle null properties from converters later in the chain" in {
    object CustomConverter extends ModelConverter {
      override def resolve(`type`: Type, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Schema[_] = {
        if (chain.hasNext) chain.next().resolve(`type`, context, chain) else null
      }

      override def resolve(`type`: Type, context: ModelConverterContext, annotations: Array[Annotation],
                                   chain: util.Iterator[ModelConverter]): Schema[_] = {
        null
      }

      override def resolveAnnotatedType(`type`: Type, member: Annotated, elementName: String,
                               parent: Schema[_],
                               jsonUnwrappedHandler: BiFunction[JavaType, Array[Annotation], Schema[_]],
                               context: ModelConverterContext,
                               chain: Iterator[ModelConverter]): Schema[_] = {
        if (chain.hasNext()) {
          chain.next().resolveAnnotatedType(`type`, member, elementName, parent, jsonUnwrappedHandler, context, chain)
        } else {
          null
        }
      }
    }

    val converter = new ModelConverters()
    converter.addConverter(CustomConverter)
    converter.addConverter(new SwaggerScalaModelConverter)
    converter.readAll(classOf[Option[Int]])
  }

  def findModel(schemas: Map[String, Schema[_]], name: String): Option[Schema[_]] = {
    schemas.get(name) match {
      case Some(m) => Some(m)
      case None =>
        schemas.keys.find { case k => k.startsWith(name) } match {
          case Some(key) => schemas.get(key)
          case None => schemas.values.headOption
        }
    }
  }

  def nullSafeList[T](list: java.util.List[T]): List[T] = Option(list) match {
    case None => List[T]()
    case Some(l) => l.asScala.toList
  }
}
