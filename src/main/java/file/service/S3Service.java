package file.service;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    private final String DIR_NAME = "s3_data";
    
    // 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		
		// C:/CE/98.data/s3_data에 파일 저장 -> S3 전송 및 저장 (putObject)
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		
		// DB 저장
		String savePath = "/home/ubuntu/" + DIR_NAME;
		String attachmentOriginalFileName = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString() + "_" + file.getOriginalFilename();
		Long attachmentFileSize = file.getSize();
		
		AttachmentFile attachmentFile = AttachmentFile.builder().
				attachmentFileName(attachmentFileName).
				attachmentOriginalFileName(attachmentOriginalFileName).
				filePath(savePath).attachmentFileSize(attachmentFileSize).
				build();
		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
		
		// s3 물리적으로 저장
		if(fileNo != null) {
			//임시 파일 저장
			File uploadFile = new File(attachmentFile.getFilePath() + "//" + attachmentFileName);
			file.transferTo(uploadFile);
			
			// s3 파일 전송
			// bucket : 버킷
			// key : 객체의 저장경로 + 객체의 이름
			// file : 물리적인 리소스
			String key = DIR_NAME + "/" + uploadFile.getName();
			
			amazonS3.putObject(bucketName, key, uploadFile);
			
			//임시 퍼일 삭제
			if(uploadFile.exists()) {
				uploadFile.delete();
			}
		}
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile = fileRepository.findById(fileNo)
										.orElseThrow(() -> new NoSuchElementException("파일 없음"));
		String key = DIR_NAME + "/" + attachmentFile.getAttachmentFileName();
		
		S3Object s3Object = amazonS3.getObject(bucketName, key);
		S3ObjectInputStream s3ois = s3Object.getObjectContent();
		resource = new InputStreamResource(s3ois);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition
										.builder("attachment")
										.filename(attachmentFile.getAttachmentOriginalFileName())
										.build());
		
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
	}
	
}
