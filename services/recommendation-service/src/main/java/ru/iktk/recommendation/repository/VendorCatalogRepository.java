package ru.iktk.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.VendorCatalog;

import java.util.List;

@Repository
public interface VendorCatalogRepository extends JpaRepository<VendorCatalog, String> {

    List<VendorCatalog> findByEquipmentType(String equipmentType);

    @Query("SELECT v FROM VendorCatalog v WHERE " +
           "(:equipmentType IS NULL OR v.equipmentType = :equipmentType) AND " +
           "(:maxPrice IS NULL OR v.price <= :maxPrice) AND " +
           "(:maxLeadTimeDays IS NULL OR v.leadTimeDays <= :maxLeadTimeDays) AND " +
           "(:region IS NULL OR v.region = :region) AND " +
           "v.active = true")
    List<VendorCatalog> search(@Param("equipmentType") String equipmentType,
                               @Param("maxPrice") Double maxPrice,
                               @Param("maxLeadTimeDays") Integer maxLeadTimeDays,
                               @Param("region") String region);
}