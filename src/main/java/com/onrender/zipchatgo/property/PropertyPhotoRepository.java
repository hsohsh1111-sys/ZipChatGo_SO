package com.onrender.zipchatgo.property;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PropertyPhotoRepository extends CrudRepository<PropertyPhoto, Long> {

    List<PropertyPhoto> findByPropertyIdOrderByIdAsc(Long propertyId);

    // 대표 사진(sortOrder 0)부터 순서대로
    List<PropertyPhoto> findByPropertyIdOrderBySortOrderAscIdAsc(Long propertyId);
}
