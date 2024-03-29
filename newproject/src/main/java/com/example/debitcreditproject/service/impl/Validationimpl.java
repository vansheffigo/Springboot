package com.example.debitcreditproject.service.impl;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.debitcreditproject.entity.GrnEntity;
import com.example.debitcreditproject.entity.GrnItem;
import com.example.debitcreditproject.entity.InvoiceEntity;
import com.example.debitcreditproject.entity.MasterData;
import com.example.debitcreditproject.entity.MaterialEntity;
import com.example.debitcreditproject.entity.PoEntity;
import com.example.debitcreditproject.entity.PoItem;
import com.example.debitcreditproject.entity.VendorEntity;
import com.example.debitcreditproject.repository.GrnItemRepo;
import com.example.debitcreditproject.repository.GrnRepo;
import com.example.debitcreditproject.repository.InvoiceRepo;
import com.example.debitcreditproject.repository.MasterDataRepo;
import com.example.debitcreditproject.repository.MaterialRepo;
import com.example.debitcreditproject.repository.PoItemRepo;
import com.example.debitcreditproject.repository.PoRepo;
import com.example.debitcreditproject.repository.VendorRepo;
import com.example.debitcreditproject.service.ValidationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class Validationimpl implements ValidationService {

	GrnRepo grnRepo;
	InvoiceRepo invoiceRepo;
	MaterialRepo materialRepo;
	PoRepo poRepo;
	VendorRepo vendorRepo;
	PoItemRepo poItemRepo;
	GrnItemRepo grnItemRepo;
	Boolean remarksok = false;
	JsonArray jsonArray;
	MasterDataRepo masterDataRepo;

	public Validationimpl(GrnRepo grnRepo, InvoiceRepo invoiceRepo, MaterialRepo materialRepo, PoRepo poRepo,
			VendorRepo vendorRepo, PoItemRepo poItemRepo, GrnItemRepo grnItemRepo, MasterDataRepo masterDataRepo) {
		super();
		this.grnRepo = grnRepo;
		this.invoiceRepo = invoiceRepo;
		this.materialRepo = materialRepo;
		this.poRepo = poRepo;
		this.vendorRepo = vendorRepo;
		this.poItemRepo = poItemRepo;
		this.grnItemRepo = grnItemRepo;
		this.masterDataRepo = masterDataRepo;
	}

	private String getStringValue(Cell cell) {
		if (cell != null) {
			cell.setCellType(CellType.STRING); // Ensure cell is read as string
			return cell.getStringCellValue();
		}
		return null;
	}

	public boolean containsComma(String number) {
		// Regular expression to check if the number contains a comma
		Pattern pattern = Pattern.compile(".*[,].*");
		return pattern.matcher(number).matches();
	}

	public boolean isValidInvoiceNumber(String invoiceNumber) {
		// Regular expression to allow alphanumeric characters, hyphen (-), and slash

		String regex = "^[a-zA-Z0-9/-]{1,16}$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(invoiceNumber);
		return matcher.matches();
	}

	public boolean isValidDate(String date) {

		String regex = "^(0[1-9]|[12][0-9]|3[01])-(0[1-9]|1[0-2])-(\\d{4})$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(date);
		return matcher.matches();
	}

	private Long getLongNumericValue(Cell cell) {
		if (cell != null && cell.getCellType() == CellType.NUMERIC) {
			DataFormatter dataFormatter = new DataFormatter();
			String cellValue = dataFormatter.formatCellValue(cell);
			try {
				return Long.parseLong(cellValue);
			} catch (NumberFormatException e) {

				return 0L; // Return default value on error
			}
		}
		return 0L;
	}

	public static Date getJavaDate(double excelDate) {
		final long daysToMilliseconds = 24 * 60 * 60 * 1000; // Convert days to milliseconds
		final long epochDiff = 25569;
		long milliseconds = (long) ((excelDate - epochDiff) * daysToMilliseconds);
		return new Date(milliseconds);
	}

	public String validationFunction(MultipartFile file, List<String> links) {

		jsonArray = new JsonArray();
		try {

			String contentType = file.getContentType();
			if (!("application/vnd.ms-excel".equals(contentType))) {
				return "Invalid file format. Please upload an Excel file.";
			}

			// Read Excel file content
			Workbook workbook = WorkbookFactory.create(file.getInputStream());
			Sheet sheet = workbook.getSheetAt(0); // Assuming first sheet

			int rowIterator = 1;
			int remarksokcal = 1;
			while (sheet.getRow(rowIterator).getCell(0) != null) {
				Row rowdata = sheet.getRow(rowIterator);
//				List<String> rowList = new ArrayList<>();

				StringBuilder buffer = new StringBuilder();
				if (rowdata != null) {
					VendorEntity vendorEntity = new VendorEntity();
					GrnEntity grnEntity = new GrnEntity();
					InvoiceEntity invoiceEntity = new InvoiceEntity();
					MaterialEntity materialEntity = new MaterialEntity();
					PoEntity poEntity = new PoEntity();
					PoItem poItem = new PoItem();
					GrnItem grnItem = new GrnItem();

					String vendorCode = getStringValue(rowdata.getCell(0));
					String poNumber = getStringValue(rowdata.getCell(1));
					String materialCode = getStringValue(rowdata.getCell(2));
					Long quantity = getLongNumericValue(rowdata.getCell(3));
					String grnNumber = getStringValue(rowdata.getCell(4));
					String grndate = new DataFormatter().formatCellValue(rowdata.getCell(5));
					String invoiceNumber = getStringValue(rowdata.getCell(6));

					String invoicedate = new DataFormatter().formatCellValue(rowdata.getCell(7));

					Long dispatchedquantity = getLongNumericValue(rowdata.getCell(8));
					String poAmount = getStringValue(rowdata.getCell(9));

					String gstStr = getStringValue(rowdata.getCell(10));
					double gstPercentage = Double.parseDouble(gstStr);
					System.out.println("Reached1 ");
					int gstInteger = (int) (gstPercentage * 100);
					String invoiceAmount = getStringValue(rowdata.getCell(11));
					JsonObject jsonObject = new JsonObject();

					log.info("fetching Data from excel" + "vendorCode" + vendorCode + "poNumber",
							poNumber + "materialCode", materialCode + "poItem", quantity);
					jsonObject.addProperty("vendorCode", vendorCode);
					jsonObject.addProperty("poNumber", poNumber);
					jsonObject.addProperty("materialCode", materialCode);
					jsonObject.addProperty("poItem", quantity);
					jsonObject.addProperty("grnNumber", grnNumber);
					if (!isValidDate(grndate)) {
						jsonObject.addProperty("grnDate", grndate);
						buffer.append("The date format for grndate should be dd-mm-yyyy" + " ");
					} else {
						jsonObject.addProperty("grnDate", grndate);
					}
					if (!isValidInvoiceNumber(invoiceNumber)) {
						jsonObject.addProperty("invoiceNumber", invoiceNumber);
						buffer.append(
								"Invoice number should not contain spaces or any special characters and          should not be gretter than 16 characters ");
					} else {
						jsonObject.addProperty("invoiceNumber", invoiceNumber);
					}
					if (!isValidDate(invoicedate)) {
						jsonObject.addProperty("invoiceDate", invoicedate);
						buffer.append("The date format for invoicedate should be dd-mm-yyyy" + " ");
					} else {
						jsonObject.addProperty("invoiceDate", invoicedate);

					}
					Optional<MasterData> masterDataOptional = masterDataRepo.findByGrnNumber(grnNumber);
					if (masterDataOptional.isPresent()) {
						if (masterDataRepo.findQuantityByGrnNumber(grnNumber) != dispatchedquantity) {
							buffer.append("The grn quantity does not match  ");
						}

						jsonObject.addProperty("invoicequantity", dispatchedquantity);
					} else {
						buffer.append("No Such Grn Exists  ");
						jsonObject.addProperty("invoicequantity", dispatchedquantity);
					}
					if (containsComma(poAmount)) {
						jsonObject.addProperty("basicPrice", poAmount);
						buffer.append("The format for PoAmount should not contian comma" + " ");
					} else
						jsonObject.addProperty("basicPrice", poAmount);

					jsonObject.addProperty("gst", gstInteger);
					if (containsComma(invoiceAmount)) {
						jsonObject.addProperty("grandTotal", invoiceAmount);
						buffer.append("The format for InvoiceAmount should not contian comma" + " ");
					} else
						jsonObject.addProperty("grandTotal", invoiceAmount);
					if (buffer.toString().equals("")) {
						jsonObject.addProperty("Remarks", "OK");
						remarksokcal++;
					} else
						jsonObject.addProperty("Remarks", buffer.toString());
					log.info(buffer.toString().length() + "");

					rowIterator++;

					jsonArray.add(jsonObject);

				}
			}

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode arrayNode = objectMapper.readTree(jsonArray.toString());

			if (rowIterator == remarksokcal) {
				log.info("Iterating and saving after validation for remarks is done ");
				for (JsonNode element : arrayNode) {

					String invoiceNumber = element.get("invoiceNumber").asText();
					Optional<InvoiceEntity> exist = invoiceRepo.findById(invoiceNumber);
					if (exist.isPresent()) {
						continue;
					}

					String vendorCode = element.get("vendorCode").asText();
					String poNumber = element.get("poNumber").asText();
					String materialCode = element.get("materialCode").asText();
					Long quantity = element.get("poItem").asLong();
					String grnNumber = element.get("grnNumber").asText();
					String grnDate = element.get("grnDate").asText();

					String invoiceDate = element.get("invoiceDate").asText();
					Long dispatchedquantity = element.get("invoicequantity").asLong();
					String poAmount = element.get("basicPrice").asText();
					int gst = element.get("gst").asInt();
					String invoiceAmount = element.get("grandTotal").asText();
					String remarks = element.get("Remarks").asText();
					VendorEntity vendorEntity = new VendorEntity();
					GrnEntity grnEntity = new GrnEntity();
					InvoiceEntity invoiceEntity = new InvoiceEntity();
					MaterialEntity materialEntity = new MaterialEntity();
					PoEntity poEntity = new PoEntity();
					PoItem poItem = new PoItem();
					GrnItem grnItem = new GrnItem();
					vendorEntity.setVendorCode(vendorCode);
					vendorEntity = vendorRepo.save(vendorEntity);
					// Save PO
					poEntity.setPoNumber(poNumber);
					poEntity.setVendor(vendorEntity);
					poEntity = poRepo.save(poEntity);
					// Save Material
					materialEntity.setMaterialCode(materialCode);
					materialEntity = materialRepo.save(materialEntity);
					// Set references in PoItem
					poItem.setQuantity(quantity);
					poItem.setPoItemMaterial(materialEntity);
					poItem.setPoid(poEntity);
					poItem.setGst(gst);
					poItem.setPrice(poAmount);
					poItemRepo.save(poItem);
					// save Grn
					grnEntity.setGrnNumber(grnNumber);
					grnEntity.setGrnDate(grnDate);
					grnRepo.save(grnEntity);
					grnItem.setPoitem(poItem);
					grnItem.setGrn(grnEntity);
					grnItem.setDispatchedquantity(dispatchedquantity);
					grnItemRepo.save(grnItem);
					// save Invoice
					invoiceEntity.setInvoiceNumber(invoiceNumber);
					invoiceEntity.setInvoiceDate(invoiceDate);
					invoiceEntity.setGrnitems(grnItem);
					invoiceEntity.setAmount(invoiceAmount);
					invoiceRepo.save(invoiceEntity);
				}
				workbook.close();

				if (rowIterator == remarksokcal)
					remarksok = true;
				else
					remarksok = false;
			}
//			String returnData = jsonArray.toString();
			int ind = 0;

			for (JsonElement element : jsonArray) {
				JsonObject jsonObject = element.getAsJsonObject();
				jsonObject.addProperty("Attachment", links.get(ind));

				ind++;

			}
			return jsonArray.toString();
			// Return extracted data as ResponseEntity
		} catch (IOException | EncryptedDocumentException ex) {
			ex.printStackTrace();
			return "Error processing Excel file.";
		}
	}

	public Boolean uploadfun() {
		log.info("Checking the remarks for upload" + remarksok);
		return remarksok;
	}

	public String sendData() {
		log.info("Uploading data to Client frontend");
		ObjectMapper objectMapper = new ObjectMapper();

		try {
			JsonNode arrayNode = objectMapper.readTree(jsonArray.toString());

			for (JsonNode element : arrayNode) {
				((ObjectNode) element).put("status", "new");

			}
			return arrayNode.toString();
		} catch (JsonProcessingException e) {

			e.printStackTrace();
		}

		return "Not Data Found";
	}

	public String sendUpdatedData(MultipartFile file) {
		try {
			System.out.println("heelloo");
			String contentType = file.getContentType();
			System.out.println(contentType + "lop");
			if (!("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType))) {
				return "Invalid file format. Please upload an Excel file.";
			}

			Workbook workbook = WorkbookFactory.create(file.getInputStream());
			Sheet sheet = workbook.getSheetAt(0);
			int rowIterator = 1;

			ObjectMapper objectMapper = new ObjectMapper();

			JsonNode arrayNode = objectMapper.readTree(jsonArray.toString());

			for (JsonNode element : arrayNode) {
				Row rowdata = sheet.getRow(rowIterator);
				((ObjectNode) element).put("status", "new");

				((ObjectNode) element).put("BookingNumber", getStringValue(rowdata.getCell(15)));
				rowIterator++;
			}
			System.out.println(arrayNode.toString());
			return arrayNode.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return " Not Data Found";
	}

}
